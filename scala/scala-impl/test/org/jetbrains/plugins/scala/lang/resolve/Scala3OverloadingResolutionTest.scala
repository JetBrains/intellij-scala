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


  def testLateApplyExpansion(): Unit = checkHasErrorAroundCaret(
    s"""
      |class Example {
      |  class Bar { def apply(s: String): String = s; def apply(d: Double): Double = d }
      |  def foo(i: Int): Bar = ???
      |  def foo(i: Int)(s: String): String = ???
      |  fo${CARET}o(1)("213")
      |}
      |""".stripMargin
  )

  def testLateApplyExpansionInapplicable(): Unit = checkTextHasNoErrors(
    """
      |class Example {
      |  class Bar { def apply(d: Double): Double = d }
      |  def foo(i: Int): Bar = ???
      |  def foo(i: Int)(s: String): String = ???
      |  foo(1)("213")
      |}
      |""".stripMargin
  )

  def testLateApplyExpansionPolymorphicReturnType(): Unit = checkTextHasNoErrors(
    """
      |class Example {
      |  class Bar[T] { def apply(t: T): T = t }
      |  def foo(i: Int): Bar[String] = ???
      |  def foo(i: Int)(d: Double): Double = ???
      |  foo(1)("213")
      |}
      |""".stripMargin
  )

  def testLateApplyExpansionMultiClauseApplyAmbiguous(): Unit = checkHasErrorAroundCaret(
    s"""
      |class Example {
      |  class Bar { def apply(s: String)(b: Boolean): String = s }
      |  def foo(i: Int): Bar = ???
      |  def foo(i: Int)(s: String)(b: Boolean): String = ???
      |  fo${CARET}o(1)("213")(true)
      |}
      |""".stripMargin
  )

  def testLateApplyExpansionMultiClauseApplyWins(): Unit = checkTextHasNoErrors(
    """
      |class Example {
      |  class Bar { def apply(s: String)(b: Boolean): String = s }
      |  def foo(i: Int): Bar = ???
      |  def foo(i: Int)(s: String)(d: Double): String = ???
      |  foo(1)("213")(true)
      |}
      |""".stripMargin
  )

  def testLateApplyExpansionChainedApply(): Unit = doResolveTest(
    s"""
      |class Example {
      |  class Baz { def ${REFTGT}apply(b: Boolean): String = "" }
      |  class Bar { def apply(s: String): Baz = ??? }
      |  def foo(i: Int): Bar = ???
      |  def foo(i: Int)(s: String)(d: Double): String = ???
      |  ${REFSRC}foo(1)("213")(true)
      |}
      |""".stripMargin
  )

  def testLateApplyExpansionChainedApplyTyper(): Unit = checkTextHasNoErrors(
    """
      |class Example {
      |  class Baz { def apply(b: Boolean): String = "" }
      |  class Bar { def apply(s: String): Baz = ??? }
      |  def foo(i: Int): Bar = ???
      |  def foo(i: Int)(s: String)(d: Double): String = ???
      |  foo(1)("213")(true)
      |}
      |""".stripMargin
  )

  def testLateApplyExpansionFails(): Unit = checkHasErrorAroundCaret(
    s"""
       |class Foo { def apply: Bar = new Bar {} }
       |class Bar { def apply[B](b: B): String = "" }
       |def foo[A](a: A): Foo = new Foo {}
       |
       |val z = foo(1)$CARET("")
       |
       |""".stripMargin
  )


  def testLateApplyExpansionWinsInferenceDependent(): Unit = checkTextHasNoErrors(
    """
      |object a {
      |  trait Bar[A] { def apply(a: A): Int = 123 }
      |  def foo[A](i: A)(b: String): String = ???
      |  def foo[A](a: A): Bar[A] = ???
      |  val z = foo(1)(2)
      |}
      |""".stripMargin
  )

  def testLateApplyExpansionChainedApplyAmbiguous(): Unit = checkHasErrorAroundCaret(
    s"""
      |class Example {
      |  class Baz { def apply(b: Boolean): String = "" }
      |  class Bar { def apply(s: String): Baz = ??? }
      |  def foo(i: Int): Bar = ???
      |  def foo(i: Int)(s: String)(b: Boolean): String = ???
      |  fo${CARET}o(1)("213")(true)
      |}
      |""".stripMargin
  )

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

  def testSCL24199(): Unit = checkTextHasNoErrors(
    s"""
       |class MainTest {
       |  class Foo
       |
       |  extension [T](leftSideValue: T) {
       |    def shouldBe(right: Foo): Unit = ???
       |  }
       |
       |  extension [T, R](leftSideValue: T) {
       |    def shouldBe(right: R): Unit = ???
       |  }
       |
       |  shouldBe(1)(new Foo)
       |  1 shouldBe (new Foo)
       |}
       |""".stripMargin
  )

  def testSCL25049(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  def bar(x: Int): String = "1"
      |  def bar(x: Int)(t: Int): Unit = 2
      |  val z = bar(1)
      |}
      |""".stripMargin
  )

  def testOverloadedMultiArgApply(): Unit = checkTextHasNoErrors(
    s"""
       |class Example {
       |  trait Baz
       |  class Bar {
       |    def apply(s: String)(x: String): Baz = ???
       |    def apply(s: String)(x: Int): Baz = ???
       |  }
       |  def foo(i: Int): Bar = ???
       |  val zz = foo(1)("213")(1)
       |}""".stripMargin
  )

  // TODO[SIP-47]: requires cross-clause type-parameter propagation during applicability checks
  def disabledTypeInferenceScattered(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  def foo[A](a: A)(b: Int)(d: Double): String = "123"
      |  def foo[A](a: A)(b: Int)[B](c: A): Int = 123
      |  val x = foo(1)(2)("")
      |}
      |""".stripMargin
  )
}
