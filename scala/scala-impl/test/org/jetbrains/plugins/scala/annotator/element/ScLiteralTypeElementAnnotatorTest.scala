package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestLike
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

/** see also [[org.jetbrains.plugins.scala.annotator.LiteralTypesHighlightingTestBase]] */
abstract class ScLiteralTypeElementAnnotatorTestBase
  extends ScalaLightCodeInsightFixtureTestCase
    with ScalaHighlightingTestLike {

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

  def testLiteralType_InsideRange(): Unit = assertNoErrors(
    """type T1 = 2147483647 //Int.MaxValue
      |type T2 = -2147483648 //Int.MinValue
      |type T3 = 9223372036854775807L //Long.MaxValue
      |type T4 = -9223372036854775808L //Long.MinValue
      |""".stripMargin
  )

  def testLiteralType_OutsideRange(): Unit = assertErrorsText(
    """type T1 = 2147483648 //Int.MaxValue + 1
      |type T2 = -2147483649 //Int.MinValue - 1
      |type T3 = 9223372036854775808L //Long.MaxValue + 1
      |type T4 = -9223372036854775809L //Long.MinValue - 1
      |""".stripMargin,
    """Error(2147483648,Integer literal is out of range for type Int)
      |Error(-2147483649,Integer literal is out of range for type Int)
      |Error(9223372036854775808L,Integer number is out of range even for type Long)
      |Error(-9223372036854775809L,Integer number is out of range even for type Long)
      |""".stripMargin
  )

  protected val CodeWithUnicodeEscapes =
    s"""val s1: "string#" = "string#"
       |val s2: \"\"\"string\\u0023\"\"\" = "string\\u0023"
       |val s3: \"\"\"string#\"\"\" = "string\\u0023"
       |val s4: \"\"\"string\\u0023\"\"\" = "string#"
       |val s5: "test 敃" = \"\"\"test \\u6543\"\"\"
       |""".stripMargin

  def testWithUnicodeSequences(): Unit =
    assertNoErrors(
      CodeWithUnicodeEscapes
    )
}

class ScLiteralTypeElementAnnotatorTest_Scala_3 extends ScLiteralTypeElementAnnotatorTest_Scala_2_13 {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.isScala3

  override def testWithUnicodeSequences(): Unit =
    assertErrorsText(
      CodeWithUnicodeEscapes,
      s"""Error("string\\u0023",Expression of type "string#" doesn't conform to expected type "string\\\\u0023")
         |Error("string#",Expression of type "string#" doesn't conform to expected type "string\\\\u0023")
         |Error(\"\"\"test \\u6543\"\"\",Expression of type "test \\\\u6543" doesn't conform to expected type "test \\u6543")
         |""".stripMargin
    )

  def testWithUnicodeSequences_WithCompileTimeOps(): Unit =
    assertErrorsText(
      s"""import scala.compiletime.ops.string.+
         |
         |val s1: "string\\u0023" = "string\\u0023"
         |val s2: "string\\u0023" = "string" + "\\u0023"
         |val s3: "string" + "\\u0023" = "string\\u0023"
         |val s4: "string" + "\\u0023" = "string\\u0024"
         |""".stripMargin,
      s"""Error("string\\u0024",Expression of type "string$$" doesn't conform to expected type "string#")
         |""".stripMargin
    )
}