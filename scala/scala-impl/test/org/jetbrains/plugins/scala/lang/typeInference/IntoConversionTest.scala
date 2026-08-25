package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class IntoConversionTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def additionalCompilerOptions: Seq[String] = super.additionalCompilerOptions :+ "-preview"
  override def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_7

  def testFunParameter(): Unit = {
    checkTextHasNoErrors(
      """import scala.Conversion.into
        |import scala.Conversion.underlying
        |
        |trait A {
        |  def test: Unit
        |}
        |
        |given Conversion[Int, A] = ???
        |
        |def test(p: into[A]): Unit = {
        |  p.underlying.test
        |  p.test
        |  val a: A = p
        |}
        |
        |def test2(): Unit = {
        |  test(3)
        |}
        |""".stripMargin
    )
  }


  def testClassParameter(): Unit = {
    checkTextHasNoErrors(
      """import scala.Conversion.into
        |import scala.Conversion.underlying
        |
        |trait A {
        |  def test: Unit
        |}
        |
        |given Conversion[Int, A] = ???
        |
        |class Test(p: into[A]) {
        |  p.underlying.test
        |  p.test
        |  val a: A = p
        |}
        |
        |def test2(): Unit = {
        |  new Test(3)
        |}
        |""".stripMargin
    )
  }

  def testValue(): Unit = {
    checkTextHasNoErrors(
      """import scala.Conversion.into
        |import scala.Conversion.underlying
        |
        |trait A { def test: Unit }
        |
        |object Test {
        |  val a: into[A] = (null : A)
        |  a.underlying.test
        |}
        |""".stripMargin
    )

    checkHasErrorAroundCaret(
      s"""import scala.Conversion.into
        |import scala.Conversion.underlying
        |
        |trait A { def test: Unit }
        |
        |object Test {
        |  val a: into[A] = (null : A)
        |  a.t${CARET}est
        |}
        |""".stripMargin
    )
  }

  def testSummonUsingParameter(): Unit = {
    checkTextHasNoErrors(
      """import scala.Conversion.into
        |
        |trait A { def test: Unit }
        |
        |def test(using into[A]): Unit = {
        |  summon[A].test
        |}
        |""".stripMargin
    )
  }

  def testSummonUsingClassParameter(): Unit = {
    checkTextHasNoErrors(
      """import scala.Conversion.into
        |
        |trait A { def test: Unit }
        |
        |class Test(using into[A]) {
        |  summon[A].test
        |}
        |""".stripMargin
    )
  }

  // a `given into[A]` does not provide an `A`, so the summon call has no argument for its using parameter
  def testSummonNonIntoFromIntoGiven(): Unit = {
    checkHasErrorAroundCaret(
      s"""import scala.Conversion.into
         |import scala.Conversion.underlying
         |
         |trait A { def test: Unit }
         |
         |def test(): Unit = {
         |  given into[A] = ???
         |  sum${CARET}mon[A].test
         |}
         |""".stripMargin
    )
  }

  def testSummonIntoFromNonIntoGiven(): Unit = {
    checkTextHasNoErrors(
      """import scala.Conversion.into
        |
        |trait A { def test: Unit }
        |
        |def test(): Unit = {
        |  given A = null
        |  summon[into[A]].test
        |}
        |""".stripMargin
    )
  }

  def testRepeated(): Unit = {
    checkTextHasNoErrors(
      """import scala.Conversion.into
        |
        |trait A { def test: Unit }
        |
        |def test(all: into[A]*): Unit = {
        |  all.foreach(_.test)
        |
        |  given Conversion[Int, A] = ???
        |  test(3, 4)
        |}
        |""".stripMargin
    )
  }

  // SCL-25263
  def testTypeAlias(): Unit = {
    checkTextHasNoErrors(
      """import Conversion.into
        |trait Target { def fooTT: Int = 42 }
        |
        |object Test:
        |  type ToTargetType = into[Target]
        |  def foo(tt: ToTargetType): Unit =
        |    val _ = tt: Target
        |    tt.fooTT
        |""".stripMargin
    )
  }

  def testOpaqueTypeAlias(): Unit = {
    checkTextHasNoErrors(
      """import Conversion.into
        |trait Target { def fooTT: Int = 42 }
        |
        |object Test:
        |  opaque type ToTargetType = into[Target]
        |
        |  def foo(tt: ToTargetType): Unit =
        |    val _ = tt: Target
        |    tt.fooTT
        |""".stripMargin
    )
  }

  def testOpaqueTypeAlias2(): Unit =
    checkHasErrorAroundCaret(
      s"""import Conversion.into
        |trait Target { def fooTT: Int = 42 }
        |
        |object Container:
        |  opaque type ToTargetType = into[Target]
        |
        |object Test:
        |  def foo(tt: Container.ToTargetType): Unit =
        |    val _ = tt: Tar${CARET}get
        |    tt.fooTT
        |""".stripMargin
    )
}
