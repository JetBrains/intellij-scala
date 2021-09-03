package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestBase

abstract class ScLiteralTypeElementAnnotatorTestBase extends ScalaHighlightingTestBase {
  protected val SimpleLiteralTypeCode =
    """class A {
      |  def f0: 42 = ???
      |  def f1: 42 = ???
      |  def f2: 42f = ???
      |  def f3: 42d = ???
      |  def f4: 42L = ???
      |  def f5: 42.0 = ???
      |  def f6: -42.0D = ???
      |  def f7: true = ???
      |  def f8: 'c' = ???
      |  def f9: "string" = ???
      |}
      |""".stripMargin

  protected val NonSimpleLiteralTypeCode =
    """class A {
      |  def g1: null = ???
      |  def g2: s"interpolated" = ???
      |  def g3: s"interpolated ${1 + 2}" = ???
      |  def g4: 'Symbol = ???
      |}
      |""".stripMargin

  def testSimpleLiteralType(): Unit
  def testNonSimpleLiteralType(): Unit
}

class ScLiteralTypeElementAnnotatorTest_Scala_2_12 extends ScLiteralTypeElementAnnotatorTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_12

  override def testSimpleLiteralType(): Unit =
    assertErrorsText(
      SimpleLiteralTypeCode,
      """Error(42,Wrong type `42`, for literal types support use Scala 2.13 or Typelevel Scala with `-Yliteral-types` compiler flag)
        |Error(42,Wrong type `42`, for literal types support use Scala 2.13 or Typelevel Scala with `-Yliteral-types` compiler flag)
        |Error(42f,Wrong type `42f`, for literal types support use Scala 2.13 or Typelevel Scala with `-Yliteral-types` compiler flag)
        |Error(42d,Wrong type `42d`, for literal types support use Scala 2.13 or Typelevel Scala with `-Yliteral-types` compiler flag)
        |Error(42L,Wrong type `42L`, for literal types support use Scala 2.13 or Typelevel Scala with `-Yliteral-types` compiler flag)
        |Error(42.0,Wrong type `42.0`, for literal types support use Scala 2.13 or Typelevel Scala with `-Yliteral-types` compiler flag)
        |Error(-42.0D,Wrong type `-42.0D`, for literal types support use Scala 2.13 or Typelevel Scala with `-Yliteral-types` compiler flag)
        |Error(true,Wrong type `true`, for literal types support use Scala 2.13 or Typelevel Scala with `-Yliteral-types` compiler flag)
        |Error('c',Wrong type `'c'`, for literal types support use Scala 2.13 or Typelevel Scala with `-Yliteral-types` compiler flag)
        |Error("string",Wrong type `"string"`, for literal types support use Scala 2.13 or Typelevel Scala with `-Yliteral-types` compiler flag)
        |""".stripMargin
    )

  override def testNonSimpleLiteralType(): Unit =
    assertErrorsText(
      NonSimpleLiteralTypeCode,
      """Error(null,Wrong type `null`, for literal types support use Scala 2.13 or Typelevel Scala with `-Yliteral-types` compiler flag)
        |Error(s"interpolated",Wrong type `s"interpolated"`, for literal types support use Scala 2.13 or Typelevel Scala with `-Yliteral-types` compiler flag)
        |Error(s"interpolated ${1 + 2}",Wrong type `s"interpolated ${1 + 2}"`, for literal types support use Scala 2.13 or Typelevel Scala with `-Yliteral-types` compiler flag)
        |Error('Symbol,Wrong type `'Symbol`, for literal types support use Scala 2.13 or Typelevel Scala with `-Yliteral-types` compiler flag)
        |""".stripMargin
    )
}

class ScLiteralTypeElementAnnotatorTest_Scala_2_13 extends ScLiteralTypeElementAnnotatorTest_Scala_2_12 {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  override def testSimpleLiteralType(): Unit =
    assertNoErrors(SimpleLiteralTypeCode)

  override def testNonSimpleLiteralType(): Unit =
    assertErrorsText(
      NonSimpleLiteralTypeCode,
      """Error(null,An identifier expected, but 'null' found)
        |Error(s"interpolated",An identifier expected, but string interpolator found)
        |Error(s"interpolated ${1 + 2}",An identifier expected, but string interpolator found)
        |Error('Symbol,An identifier expected, but quoted identifier found)
        |""".stripMargin
    )
}

class ScLiteralTypeElementAnnotatorTest_Scala_3_0 extends ScLiteralTypeElementAnnotatorTest_Scala_2_13 {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0
}