package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}

class UnderSpecifiedTypeParamImplicits extends TypeInferenceTestBase with ImplicitParametersTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  // A *lower* bound does not make the expected type searchable: the compiler refuses with
  // "No implicit search was attempted ... since the expected type M is not specific enough".
  def testUsingLowerBoundedNotSearched(): Unit = checkHasImplicitArgumentProblems(
    s"""
       |class Box[A]
       |
       |def f[A, M >: Box[A]](using M): (A, M) = ???
       |
       |${START}f${END}
       |""".stripMargin
  )

  // ...even when an implicit is in scope (compiler still refuses the search).
  def testUsingLowerBoundedNotSearchedWithGiven(): Unit = checkHasImplicitArgumentProblems(
    s"""
       |class Box[A]
       |given String = "s"
       |
       |def f[A, M >: Box[A]](using M): (A, M) = ???
       |
       |${START}f${END}
       |""".stripMargin
  )

  // Recursive lower bound (`M >: Mode[F]`) with a higher-kinded reference: still "not specific
  // enough" in the compiler — the lower bound does not make the expected type searchable.
  def testUsingRecursiveLowerBoundedNotSearched(): Unit = checkHasImplicitArgumentProblems(
    s"""
       |trait Mode[X[_]]
       |
       |def fallible[F[_], M >: Mode[F]](using M): (F[Int], M) = null
       |
       |${START}fallible${END}
       |""".stripMargin
  )

  // The implicit hints / X-Ray path (`ImplicitCollector.probableArgumentsFor`) re-runs the search
  // with `fullInfo = true`; it must not present candidates for an underspecified expected type either.
  def testUsingRecursiveLowerBoundedNoProbableArguments(): Unit = {
    val implicits = getImplicitArguments(
      s"""
         |trait Mode[X[_]]
         |
         |def fallible[F[_], M >: Mode[F]](using M): (F[Int], M) = null
         |
         |${START}fallible${END}
         |""".stripMargin
    )
    val param = implicits.head
    assertTrue("Expected an implicit parameter problem", param.isImplicitParameterProblem)

    val probable = ImplicitCollector.probableArgumentsFor(param)
    assertTrue(
      s"No probable arguments expected for an underspecified expected type, got: ${probable.map(_._1.name)}",
      probable.isEmpty
    )
  }

  // Unbounded type variable: also "not specific enough" in the compiler, even with a given in scope.
  def testUsingUnboundedNotSearched(): Unit = checkHasImplicitArgumentProblems(
    s"""
       |given String = "s"
       |
       |def f[M](using M): M = ???
       |
       |${START}f${END}
       |""".stripMargin
  )

  def testUnderspecifiedExtension(): Unit = checkTextHasNoErrors(
    s"""
       |extension [A](a: A)
       |  def test: Unit = ()
       |
       |1.test
       |
       |""".stripMargin
  )

  def testUnderspecifiedOfExtension(): Unit = checkHasImplicitArgumentProblems(
    s"""
       |extension [A](a: A)
       |  def test[B](using B): Unit = ()
       |
       |${START}1.test$END
       |
       |""".stripMargin
  )

  def testUnderspecifiedOnOuter(): Unit = checkTextHasNoErrors(
    s"""
       |class Test[Outer] {
       |  def test(using Outer): Unit = ()
       |}
       |
       |given String = "s"
       |new Test[String].test
       |""".stripMargin
  )

  def testConstructorInvocation(): Unit = checkHasImplicitArgumentProblems(
    s"""
       |
       |class Test[Outer](using Outer)
       |
       |${START}new Test$END
       """.stripMargin
  )

  // `summon[T](using x: T): x.type` hides `T` behind the singleton type of its own parameter, where
  // the expected type used to be unable to reach it, leaving `T` unbounded and the search refused as
  // underspecified - see InferUtil.exposeTypeParametersBehindSingletons.
  def testSummonWithExpectedTypeIsSearched(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait RecordLike[R]
       |given RecordLike[Int] = ???
       |
       |val r: RecordLike[Int] = ${START}summon${END}
       |""".stripMargin
  )

  def testSummonOfSimpleTypeIsSearched(): Unit = checkNoImplicitParameterProblems(
    s"""
       |given Int = 1
       |
       |val i: Int = ${START}summon${END}
       |""".stripMargin
  )

  // The same for a hand-written `summon`, and for one that wraps the argument rather than
  // returning it directly.
  def testDependentResultTypeIsSearched(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait RecordLike[R]
       |given RecordLike[Int] = ???
       |
       |def mySummon[T](using x: T): x.type = x
       |
       |val r: RecordLike[Int] = ${START}mySummon${END}
       |""".stripMargin
  )

  def testWrappedDependentResultTypeIsSearched(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait RecordLike[R]
       |class Wrapper[A]
       |given RecordLike[Int] = ???
       |
       |def mySummon[T](using x: T): Wrapper[x.type] = ???
       |
       |val r: Wrapper[RecordLike[Int]] = ${START}mySummon${END}
       |""".stripMargin
  )

  // Without an expected type nothing can constrain `M`, so the search is still refused.
  def testResultTypeMentioningTypeParameterNotSearched(): Unit = checkHasImplicitArgumentProblems(
    s"""
       |class Box[A]
       |given Box[Int] = ???
       |
       |def f[A, M >: Box[A]](using M): (A, M) = ???
       |
       |${START}f${END}
       |""".stripMargin
  )

  // A search that was attempted and found nothing must be reported as such, for the type parameters
  // inferred from the expected type - not as a refused search for the unconstrained type parameter.
  def testMissingImplicitIsReportedForTheExpectedType(): Unit = {
    val param = implicitArgumentsProblems(
      s"""
         |val i: Int = ${START}summon${END}
         |""".stripMargin
    ).head

    assertFalse(
      "The search is constrained by the expected type, so it must be attempted",
      ImplicitCollector.isTooUnspecificToSearch(param)
    )
    assertEquals(Some("Int"), ImplicitCollector.expectedTypeText(param))
  }
}
