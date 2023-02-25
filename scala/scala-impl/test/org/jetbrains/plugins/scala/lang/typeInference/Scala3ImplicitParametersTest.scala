package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3ImplicitParametersTest extends ImplicitParametersTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testSimpleGiven(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object Test {
       |  given int: Int = 123
       |  ${START}implicitly[Int]$END
       |}
       |""".stripMargin
  )

  def testAnonymousGiven(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object Test {
       |  given String = "s"
       |  def foo(using String): Unit = ???
       |  ${START}foo$END
       |}
       |""".stripMargin
  )

  def testSpecExample(): Unit = checkNoImplicitParameterProblems(
    s"""
       |
       |object A {
       |  trait Ord[T]:
       |    def compare(x: T, y: T): Int
       |
       |  given intOrd: Ord[Int] with
       |    def compare(x: Int, y: Int) = ???
       |
       |  given listOrd[T](using ord: Ord[T]): Ord[List[T]] with
       |    def compare(xs: List[T], ys: List[T]): Int = ???
       |
       |  ${START}implicitly[Ord[List[Int]]]$END
       |}
       |""".stripMargin
  )

  def testAliasWithParameters(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object A {
       |  trait Config
       |  trait Factory
       |  given Config = ???
       |  given (using config: Config): Factory = ???
       |  ${START}implicitly[Factory]$END
       |}
       |""".stripMargin
  )

  def testPatternBoundInstance(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object A {
       |  trait Context
       |  val maybeCtx: Option[Context] = ???
       |  for given Context <- maybeContext do
       |    ${START}implicitly[Context]$END
       |}
       |""".stripMargin
  )

  def testPatternBoundInstanceNested(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object A {
       |  trait Context
       |  val maybeCtx: Option[Context] = ???
       |  (maybeCtx, 1) match {
       |    case (Some(ctx @ given Context), x) => {
       |      ${START}implicitly[Context]$END
       |    }
       |  }
       |}
       |""".stripMargin
  )

  def testSCL20919(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait Result[T]:
       |  def res: T
       |
       |def run() =
       |  given Result[String] with {def res = "result"}
       |  ${START}implicitly[Result[String]]$END
       |""".stripMargin
  )
}
