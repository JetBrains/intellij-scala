package org.jetbrains.plugins.scala.lang.resolve
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase.{REFSRC, REFTGT}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3OverloadingResolutionTest extends SimpleResolveTestBase {
  override protected def supportedIn(version: ScalaVersion) =
    version >= LatestScalaVersions.Scala_3_LTS

  def testSimple(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  def foo(x: Int)(y: String): Unit = ???
      |  def foo(x: Int)(z: Double): Unit = ???
      |
      |  foo(1)("123")
      |}
      |""".stripMargin
  )

  def testDiffParamClausesSize(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  def foo(x: Int)(y: String): Unit = ???
      |  def foo(x: Int): Unit = ???
      |
      |  foo(1)("123")
      |}
      |""".stripMargin
  )


  //@TODO
//  def testLateApplyExpansion(): Unit = checkTextHasNoErrors(
//    """
//      |class Example {
//      |  class Bar { def apply(s: String): String = s; def apply(d: Double): Double = d }
//      |  def foo(i: Int): Bar = ???
//      |  def foo(i: Int)(s: String): String = ???
//      |  foo(1)("213")
//      |}
//      |""".stripMargin
//  )

  def testDecideByFirstClause(): Unit = checkHasErrorAroundCaret(
    s"""
      |object A {
      |  trait Foo[A]
      |  def foo[A](a: A)(i: Int): Int = 123
      |  def foo[A](f: Foo[A])(s: String): Int = 456
      |
      |  val ff: Foo[Int] = ???
      |  foo(ff)(1${CARET}23)
      |}
      |""".stripMargin
  )

  def testSCL23244(): Unit = doResolveTest(
    s"""
       |object A {
       |  trait FiniteDuration
       |  trait Callable[A]
       |  trait RaceCtx[T, R]
       |  trait Subtask[T]
       |
       |  def delay${REFTGT}Task[A <: T, T](delay: FiniteDuration)(callable: Callable[A]): RaceCtx[T, Subtask[T]] = ???
       |
       |  // Edit here: Intellij incorrectly lists `delayTask()` as tail recursive.
       |  // Edit here: `delayTask()` works as expected and is not recursive.
       |  def delayTask[A <: T, T](delay: FiniteDuration)(f: => A): RaceCtx[T, Subtask[T]] = {
       |    val callable: Callable[A] = ???
       |    d${REFSRC}elayTask(delay)(callable)
       |  }
       |}
       |""".stripMargin
  )

  def testSCL23657(): Unit = doResolveTest(
    s"""
       |object Test {
       |  trait Gen[+A]
       |
       |  object Gen:
       |    extension [A](self: Gen[A])
       |      def flatMap[B](f: A => Gen[B]): Gen[B] = ???
       |
       |      def lis${REFTGT}tOfN(size: Int): Gen[List[A]] = ???
       |
       |      def listOfN(size: Gen[Int]): Gen[List[A]] =
       |        size.flatMap(lis${REFSRC}tOfN)
       |}
       |""".stripMargin
  )

  def testSCL23356(): Unit = checkTextHasNoErrors(
    s"""
       |class TestA {
       |  def testWithIArray(): Unit = {
       |    val array = IArray(1, 2)
       |    val value1 = array(0)
       |    val value2 = array.apply(0)
       |    value1 + value2
       |  }
       |}
       |""".stripMargin
  )
}
