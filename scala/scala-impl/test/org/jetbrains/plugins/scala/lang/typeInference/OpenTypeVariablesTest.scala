package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

/**
 * The Scala 3 compiler leaves type variables that occur non-variantly in the type of an expression uninstantiated
 * (`Inferencing.interpolateTypeVars`), so that they can still be constrained when the types of the branches
 * of an `if`, a `match` or a `try` are joined (`Namer.inferredResultType` → `widenUnion` → `lub(canConstrain = true)`).
 *
 * The expected types are the ones inferred by Scala 3.7.4 (and 3.3.7).
 */
class OpenTypeVariablesTest_Scala3 extends TypeInferenceTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  private def doTest(definitions: String, selected: String, expected: String): Unit = doTest(
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
  )

  def testIfSingletonBranch(): Unit = doTest("val a = if (c) xs else Set.empty", "a", "Set[Foo]")

  def testIfTypedBranch(): Unit = doTest("val a = if (c) (xs: Set[Foo]) else Set.empty", "a", "Set[Foo]")

  def testIfSwappedBranches(): Unit = doTest("val a = if (c) Set.empty else xs", "a", "Set[Foo]")

  def testIfMethodCallBranch(): Unit = doTest("val a = if (c) f() else Set.empty", "a", "Set[Foo]")

  def testIfBlockBranch(): Unit = doTest("val a = if (c) xs else { println(); Set.empty }", "a", "Set[Foo]")

  def testIfBlockAroundIf(): Unit = doTest("val a = { if (c) (xs: Set[Foo]) else Set.empty }", "a", "Set[Foo]")

  def testIfParenthesizedBranch(): Unit = doTest("val a = if (c) { println(); (xs: Set[Foo]) } else (Set.empty)", "a", "Set[Foo]")

  def testIfApplyWithoutArguments(): Unit = doTest("val a = if (c) xs else Set()", "a", "Set[Foo]")

  def testIfElseIfChain(): Unit = doTest("val a = if (c) xs else if (c) Set.empty else Set()", "a", "Set[Foo]")

  def testIfLowerBoundedTypeVariable(): Unit = doTest("val a = if (c) Set(1) else anys", "a", "Set[Any]")

  def testIfTwoOpenTypeVariables(): Unit = doTest("""val a = if (c) Set(1) else Set("a")""", "a", "Set[Int | String]")

  def testIfInvariantAndCovariantTypeArguments(): Unit = doTest("val a = if (c) m else Map.empty", "a", "Map[String, Int]")

  def testIfNested(): Unit = doTest("val a = if (c) (if (c) (xs: Set[Foo]) else Set.empty) else Set.empty", "a", "Set[Foo]")

  def testIfNestedAllOpen(): Unit = doTest("val a = if (c) (if (c) Set.empty else Set.empty) else (??? : Set[Foo])", "a", "Set[Foo]")

  def testMatch(): Unit = doTest("val a = c match { case true => xs; case _ => Set.empty }", "a", "Set[Foo]")

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

  // The compiler instantiates the type variables of a scrutinee without constraining them, so it infers
  // Set[? <: Foo] (Set[Foo] | Set[Nothing] joined), which is presented as Set[SyntheticLub], SCL-26357
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
