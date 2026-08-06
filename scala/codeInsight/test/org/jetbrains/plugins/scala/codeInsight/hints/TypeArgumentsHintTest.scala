package org.jetbrains.plugins.scala.codeInsight.hints

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInsight.InlayHintsTestBase
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

class TypeArgumentsHintTest extends InlayHintsTestBase {
  import Hint.{End => E, Start => S}

  override def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  private def doTest(text: String): Unit = {
    val old = ScalaApplicationSettings.XRAY_SHOW_TYPE_ARGUMENT_HINTS
    try {
      ScalaHintsSettings.xRayMode = true
      ScalaApplicationSettings.XRAY_SHOW_TYPE_ARGUMENT_HINTS = true
      doInlayTest(text)
    } finally {
      ScalaHintsSettings.xRayMode = false
      ScalaApplicationSettings.XRAY_SHOW_TYPE_ARGUMENT_HINTS = old
    }
  }

  def testSimple(): Unit = doTest(
    s"""
       |def test[T](t: T): T = t
       |
       |test$S[Int]$E(1)
       |""".stripMargin
  )

  def testMultipleArgumentLists(): Unit = doTest(
    s"""
       |def test[T, S](t: T)(s: S): Unit = ()
       |
       |test$S[Int, String]$E(1)("str")
       |""".stripMargin
  )

  def testInterleavedArgumentLists(): Unit = doTest(
    s"""
       |def test[T](t: T)[S](s: S): Unit = ()
       |
       |test$S[Int]$E(1)$S[String]$E("str")
       |""".stripMargin
  )

  def testTypeParameterBounds(): Unit = doTest(
    s"""
       |trait Base
       |trait Derived extends Base
       |
       |def test[A >: Base](a: A): A = ???
       |
       |val z: Base = test$S[Base]$E(new Derived {})
       |val z2$S: Base$E = test$S[Base]$E(new Derived {})
       |""".stripMargin
  )

  def testNamedTypeArgumentAndInferredTypeArgument(): Unit = doTest(
    s"""
       |import scala.language.experimental.namedTypeArguments
       |
       |def test[T, S](t: T)(s: S): Unit = ()
       |
       |test[T = Int$S, S = String$E](1)("str")
       |""".stripMargin
  )

  def testNamedTypeArgumentAndInferredInterleavedTypeArgument(): Unit = doTest(
    s"""
       |import scala.language.experimental.namedTypeArguments
       |
       |def test[T](t: T)[S](s: S): Unit = ()
       |
       |test[T = Int](1)$S[String]$E("str")
       |""".stripMargin
  )

  def testNamedTypeArgumentAndInferredTypeArgumentInInterleavedClause(): Unit = doTest(
    s"""
       |import scala.language.experimental.namedTypeArguments
       |
       |def test[T](t: T)[S, R](s: S, r: R): Unit = ()
       |
       |test[T = Int](1)[S = String$S, R = Boolean$E]("str", true)
       |""".stripMargin
  )

  def testApply(): Unit = doTest(
    s"""
       |object Test {
       |  def apply[T](t: T): T = t
       |}
       |
       |Test$S.apply$E$S[Int]$E(3)
       |""".stripMargin
  )

  def testNotInferred(): Unit = doTest(
    s"""
       |trait A
       |trait B extends A
       |
       |def test[T >: B](t: T): T = t
       |
       |test$S[Any]$E()
       |""".stripMargin
  )

  def testInfix(): Unit = doTest(
    s"""object Test {
       |  def test[T](t: T): T = t
       |}
       |
       |Test test$S[Int]$E 1
       |""".stripMargin
  )

  def testApplyInTheMiddle(): Unit = doTest(
    s"""
       |class Ret {
       |  def apply[T, S](t: T)(s: S): Unit = t
       |}
       |
       |def test[T, S](t: T)(s: S): Ret = ???
       |
       |test$S[Int, String]$E(1)("str")$S.apply$E$S[Boolean, Double]$E(true)(1.0)
       |
       |""".stripMargin
  )

  def testConstructor(): Unit = doTest(
    s"""
       |class Test[T, S](t: T, s: S)
       |
       |new Test$S[Int, String]$E(1, "str")
       |
       |""".stripMargin
  )

  def testPolymorphicLambda(): Unit = doTest(
    s"""
       |class Ret {
       |  def apply[T, S](t: T)(s: S): Unit = t
       |}
       |
       |val x$S: PolyFunction{def apply[T](i: T): Ret}$E = [T] => (i: T) => new Ret
       |x$S.apply$E$S[Boolean]$E(true)$S.apply$E$S[String, Int]$E("blub")(3)
       |""".stripMargin
  )
}
