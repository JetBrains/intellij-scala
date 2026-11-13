package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

/**
 * The Scala 3 compiler leaves type variables that occur non-variantly in the type of an expression uninstantiated
 * (`Inferencing.interpolateTypeVars`), so that they can still be constrained when the types of the branches
 * of an `if`, a `match` or a `try` are joined (`Namer.inferredResultType` → `widenUnion` → `lub(canConstrain = true)`).
 *
 * The expected types are the ones inferred by Scala 3.7.4 (and 3.3.7).
 */
abstract class OpenTypeVariablesTestBase_Scala3 extends TypeInferenceTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  protected def doTest(definitions: String, selected: String, expected: String): Unit =
    doTest(fileText(definitions, selected, expected))

  protected final def fileText(definitions: String, selected: String, expected: String): String =
    s"""trait Foo
       |
       |object Test {
       |  val xs: Set[Foo] = ???
       |  val anys: Set[Any] = ???
       |  val m: Map[String, Int] = ???
       |  val c: Boolean = ???
       |  def f(): Set[Foo] = ???
       |  def id[T](t: T): T = t
       |  def over(s: Set[Foo]): Int = 1
       |  def over(i: Int): Int = 2
       |  extension (s: Set[Foo]) def ext: Int = 1
       |
       |  $definitions
       |
       |  $START$selected$END
       |}
       |//$expected
       |""".stripMargin
}

class OpenTypeVariablesTest_Scala3 extends OpenTypeVariablesTestBase_Scala3 {

  def testIfSingletonBranch(): Unit = doTest("val a = if (c) xs else Set.empty", "a", "Set[Foo]")

  def testIfTypedBranch(): Unit = doTest("val a = if (c) (xs: Set[Foo]) else Set.empty", "a", "Set[Foo]")

  def testIfSwappedBranches(): Unit = doTest("val a = if (c) Set.empty else xs", "a", "Set[Foo]")

  def testIfMethodCallBranch(): Unit = doTest("val a = if (c) f() else Set.empty", "a", "Set[Foo]")

  def testIfBlockBranch(): Unit = doTest("val a = if (c) xs else { println(); Set.empty }", "a", "Set[Foo]")

  def testIfBlockAroundIf(): Unit = doTest("val a = { if (c) (xs: Set[Foo]) else Set.empty }", "a", "Set[Foo]")

  def testIfParenthesizedBranch(): Unit = doTest("val a = if (c) { println(); (xs: Set[Foo]) } else (Set.empty)", "a", "Set[Foo]")

  def testIfApplyWithoutArguments(): Unit = doTest("val a = if (c) xs else Set()", "a", "Set[Foo]")

  def testIfElseIfChain(): Unit = doTest("val a = if (c) xs else if (c) Set.empty else Set()", "a", "Set[Foo]")

  def testIfInvariantAndCovariantTypeArguments(): Unit = doTest("val a = if (c) m else Map.empty", "a", "Map[String, Int]")

  def testIfNested(): Unit = doTest("val a = if (c) (if (c) (xs: Set[Foo]) else Set.empty) else Set.empty", "a", "Set[Foo]")

  // the type variable of the inner Set.empty is constrained before it is unified with the one of the outer Set.empty
  def testIfNestedOpenFirst(): Unit = doTest("val a = if (c) (if (c) Set.empty else (xs: Set[Foo])) else Set.empty", "a", "Set[Foo]")

  def testIfNestedBothConstrainedBeforeUnified(): Unit = doTest(
    "val a = if (c) (if (c) Set.empty else (xs: Set[Foo])) else (if (c) Set.empty else (xs: Set[Foo]))",
    "a",
    "Set[Foo]"
  )

  // the type variable of Set.empty is unified with the one of toSet, which has the declared lower bound Foo
  def testIfDeclaredLowerBound(): Unit = doTest("val a = if (c) List.empty[Foo].toSet else Set.empty", "a", "Set[Foo]")

  def testIfDeclaredLowerBoundSwapped(): Unit = doTest("val a = if (c) Set.empty else List.empty[Foo].toSet", "a", "Set[Foo]")

  // a single comparison both unifies the type variables of the branches and constrains one of them
  def testIfSameTypeVariableTwice(): Unit = doTest(
    """def twice[A]: (Set[A], Set[A]) = ???
      |  def withFoo[C]: (Set[C], Set[Foo]) = ???
      |  val a = if (c) twice else withFoo""".stripMargin,
    "a",
    "(Set[Foo], Set[Foo])"
  )

  def testIfNestedAllOpen(): Unit = doTest("val a = if (c) (if (c) Set.empty else Set.empty) else (??? : Set[Foo])", "a", "Set[Foo]")

  def testMatch(): Unit = doTest("val a = c match { case true => xs; case _ => Set.empty }", "a", "Set[Foo]")

  // the type variable of the last Set.empty is only compared with the one of the other Set.empty
  def testMatchThreeCases(): Unit =
    doTest("val a = (1: Int) match { case 1 => xs; case 2 => Set.empty; case _ => Set.empty }", "a", "Set[Foo]")

  def testTryCatch(): Unit = doTest("val a = try xs catch { case _: Exception => Set.empty }", "a", "Set[Foo]")

  def testDef(): Unit = doTest("def a = if (c) xs else Set.empty", "a", "Set[Foo]")

  def testVar(): Unit = doTest("var a = if (c) xs else Set.empty", "a", "Set[Foo]")

  def testTuple(): Unit = doTest("val a = (if (c) (xs: Set[Foo]) else Set.empty, 1)", "a", "(Set[Foo], Int)")

  def testSelection(): Unit = doTest("val a = (if (c) (xs: Set[Foo]) else Set.empty).head", "a", "Foo")

  def testArgumentOfGenericMethod(): Unit = doTest("val a = id(if (c) (xs: Set[Foo]) else Set.empty)", "a", "Set[Foo]")

  def testArgumentOfApply(): Unit = doTest("val a = List(if (c) (xs: Set[Foo]) else Set.empty)", "a", "List[Set[Foo]]")

  def testLambdaBody(): Unit = doTest("val a = List(1).map(_ => if (c) (xs: Set[Foo]) else Set.empty)", "a", "List[Set[Foo]]")

  // Nothing constrains the type variables, so they are instantiated to their lower bounds
  def testIfNothingConstrains(): Unit = doTest("val a = if (c) Set.empty else Set.empty", "a", "Set[Nothing]")

  def testNoBranches(): Unit = doTest("val a = Set.empty", "a", "Set[Nothing]")

  def testLocalValInstantiates(): Unit = doTest("val a = if (c) Set.empty else { val e = Set.empty; e }", "a", "Set[Nothing]")

  def testUnionThatIsNotJoined(): Unit = doTest("val a = if (c) Set.empty else 1", "a", "Set[Nothing] | Int")

  // The type arguments are given explicitly
  def testExplicitTypeArgument(): Unit = doTest("val a = if (c) xs else Set.empty[Foo]", "a", "Set[Foo]")

  // Other consumers of the type of an `if`
  def testArgumentOfOverloadedMethod(): Unit = doTest("val a = over(if (c) (xs: Set[Foo]) else Set.empty)", "a", "Int")

  def testExtensionMethod(): Unit = doTest("val a = (if (c) (xs: Set[Foo]) else Set.empty).ext", "a", "Int")

  def testInfixOperand(): Unit = doTest("val a = (if (c) (xs: Set[Foo]) else Set.empty) ++ xs", "a", "Set[Foo]")

  def testForComprehension(): Unit = doTest("val a = for (x <- if (c) (xs: Set[Foo]) else Set.empty) yield x", "a", "Set[Foo]")

  def testFunctionLiteralWithoutExpectedType(): Unit = doTest("val a = () => if (c) (xs: Set[Foo]) else Set.empty", "a", "() => Set[Foo]")

  // The type of the if itself, e.g. for Show Type Info or Extract Variable (the compiler's is Set[Foo] | Set[Foo])
  def testTypeOfIfItself(): Unit = doTest(
    s"""trait Foo
       |
       |object Test {
       |  val xs: Set[Foo] = ???
       |  val c: Boolean = ???
       |
       |  val a = ${START}if (c) (xs: Set[Foo]) else Set.empty$END
       |}
       |//Set[Foo]
       |""".stripMargin
  )

  private val myEitherT =
    """case class MyEitherT[A, B](value: Either[A, B]) {
      |  def flatMap[AA, BB](f: B => MyEitherT[AA, BB]): MyEitherT[AA, BB] = ???
      |  def map[BB](f: B => BB): MyEitherT[A, BB] = ???
      |}
      |
      |object MyEitherT {
      |  def rightT[B, A](a: A): MyEitherT[B, A] = ???
      |  def leftT[B, A](e: B): MyEitherT[B, A] = ???
      |}
      |
      |case class LeftValue()
      |case class RightValue()
      |""".stripMargin

  // Each branch leaves the type variable open that its argument doesn't constrain, the join solves both,
  // instead of making MyEitherT[SyntheticLub, SyntheticLub]
  def testSCL26435(): Unit = doTest(
    s"""$myEitherT
       |object Test {
       |  val isMatch: Boolean = ???
       |  val a = if isMatch then MyEitherT.rightT(()) else MyEitherT.leftT(LeftValue())
       |  ${START}a$END
       |}
       |//MyEitherT[LeftValue, Unit]
       |""".stripMargin
  )

  // The code of the issue. Note that this test doesn't reproduce the false type mismatch of the editor even before
  // the fix, where the type of the if is MyEitherT[SyntheticLub, SyntheticLub], see testSCL26435
  def testSCL26435_ForComprehension(): Unit = assertNoErrors(
    s"""$myEitherT
       |class Test1 {
       |  def intelliJ_SyntheticLub(isMatch: Boolean): MyEitherT[LeftValue, RightValue] = {
       |    for {
       |      _ <- if isMatch then MyEitherT.rightT(()) else MyEitherT.leftT(LeftValue())
       |      _ <- MyEitherT.rightT(())
       |    } yield RightValue()
       |  }
       |}
       |""".stripMargin
  )
}

/**
 * Where the plugin still infers a type other than the compiler's.
 * A test that starts failing here has been fixed, move it to [[OpenTypeVariablesTest_Scala3]].
 */
class OpenTypeVariablesTest_Scala3_Failing extends OpenTypeVariablesTestBase_Scala3 {

  override protected def shouldPass: Boolean = false

  override protected def doTest(definitions: String, selected: String, expected: String): Unit =
    doTest(fileText(definitions, selected, expected), failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail = false)

  // The argument constrains the type variable of Set(1) from below. The compiler leaves it open, since it occurs
  // non-variantly, but the plugin solves it in local type inference (`InferUtil.localTypeInferenceWithApplicabilityExt`),
  // before the branches are joined, so that the join is Set[Int] | Set[Any]
  def testIfLowerBoundedTypeVariable(): Unit = doTest("val a = if (c) Set(1) else anys", "a", "Set[Any]")

  def testIfTwoOpenTypeVariables(): Unit = doTest("""val a = if (c) Set(1) else Set("a")""", "a", "Set[Int | String]")

  // The compiler instantiates the type variables of a scrutinee without constraining them, so it infers
  // Set[? <: Foo] (Set[Foo] | Set[Nothing] joined), which is presented as Set[SyntheticLub], SCL-26357.
  // The plugin solves the type variables where the branches are joined, so it infers Set[Foo]
  def testScrutinee(): Unit = doTest("val a = (if (c) (xs: Set[Foo]) else Set.empty) match { case s => s }", "a", "Set[SyntheticLub]")
}

/** Scala 2 instantiates the type variables right away (scalac 2.13.18). */
class OpenTypeVariablesTest_Scala2 extends TypeInferenceTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13

  private def doTest(definitions: String, selected: String, expected: String): Unit = doTest(
    s"""trait Foo
       |
       |object Test {
       |  val xs: Set[Foo] = ???
       |  val anys: Set[Any] = ???
       |  val m: Map[String, Int] = ???
       |  val c: Boolean = ???
       |
       |  $definitions
       |
       |  $START$selected$END
       |}
       |//$expected
       |""".stripMargin
  )

  def testIfSingletonBranch(): Unit = doTest("val a = if (c) xs else Set.empty", "a", "Set[_ <: Foo]")

  def testIfLowerBoundedTypeVariable(): Unit = doTest("val a = if (c) Set(1) else anys", "a", "Set[_ >: Int]")

  def testIfInvariantAndCovariantTypeArguments(): Unit = doTest("val a = if (c) m else Map.empty", "a", "Map[_ <: String, Int]")
}
