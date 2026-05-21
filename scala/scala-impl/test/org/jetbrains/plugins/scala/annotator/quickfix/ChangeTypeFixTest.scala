package org.jetbrains.plugins.scala.annotator.quickfix

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.quickfix.ChangeTypeFixTestBase.DescriptionRegex
import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase

/**
 * NOTE: I test [[ChangeTypeFix]] indirectly via it's usage in annotator
 *
 * Other related tests:
 *  - [[org.jetbrains.plugins.scala.annotator.TypeMismatchHighlightingTest]]
 */
abstract class ChangeTypeFixTestBase extends ScalaAnnotatorQuickFixTestBase {}

//noinspection NotImplementedCode
abstract class ChangeTypeFixCommonTests extends ChangeTypeFixTestBase {

  override protected def description: String = "SUPPOSEDLY UNUSED DESCRIPTION (What is this anyway? Add Scaladoc)"

  override protected def descriptionMatches(s: String): Boolean = DescriptionRegex.matches(s)

  def test_Val_SingleLineDefinition(): Unit = {
    testQuickFix(
      s"""object wrapper {
         |  val string: String = ???
         |  val value: Int = ${CARET}string
         |}
         |""".stripMargin,
      s"""object wrapper {
         |  val string: String = ???
         |  val value: String = ${CARET}string
         |}
         |""".stripMargin,
      "Change type 'Int' to 'String'"
    )
  }

  def test_Var_SingleLineDefinition(): Unit = {
    testQuickFix(
      s"""object wrapper {
         |  val string: String = ???
         |  var value: Int = ${CARET}string
         |}
         |""".stripMargin,
      s"""object wrapper {
         |  val string: String = ???
         |  var value: String = ${CARET}string
         |}
         |""".stripMargin,
      "Change type 'Int' to 'String'"
    )
  }

  def test_Def_SingleLineDefinition(): Unit = {
    testQuickFix(
      s"""object wrapper {
         |  val string: String = ???
         |  def value: Int = ${CARET}string
         |}
         |""".stripMargin,
      s"""object wrapper {
         |  val string: String = ???
         |  def value: String = ${CARET}string
         |}
         |""".stripMargin,
      "Change type 'Int' to 'String'"
    )
  }

  def test_Val_BlockBody(): Unit = {
    testQuickFix(
      s"""object wrapper {
         |  val value: Int = {
         |    val string: String = ???
         |    ${CARET}string
         |  }
         |}
         |""".stripMargin,
      s"""object wrapper {
         |  val value: String = {
         |    val string: String = ???
         |    ${CARET}string
         |  }
         |}
         |""".stripMargin,
      "Change type 'Int' to 'String'"
    )
  }

  def test_Var_BlockBody(): Unit = {
    testQuickFix(
      s"""object wrapper {
         |  var value: Int = {
         |    val string: String = ???
         |    ${CARET}string
         |  }
         |}
         |""".stripMargin,
      s"""object wrapper {
         |  var value: String = {
         |    val string: String = ???
         |    ${CARET}string
         |  }
         |}
         |""".stripMargin,
      "Change type 'Int' to 'String'"
    )
  }

  def test_Def_BlockBody(): Unit = {
    testQuickFix(
      s"""object wrapper {
         |  def value: Int = {
         |    val string: String = ???
         |    ${CARET}string
         |  }
         |}
         |""".stripMargin,
      s"""object wrapper {
         |  def value: String = {
         |    val string: String = ???
         |    ${CARET}string
         |  }
         |}
         |""".stripMargin,
      "Change type 'Int' to 'String'"
    )
  }

  def test_Def_BlockBody_WithReturnStatement(): Unit = {
    testQuickFix(
      s"""object wrapper {
         |  def value: Int = {
         |    val string: String = ???
         |    return ${CARET}string
         |  }
         |}
         |""".stripMargin,
      s"""object wrapper {
         |  def value: String = {
         |    val string: String = ???
         |    return ${CARET}string
         |  }
         |}
         |""".stripMargin,
      "Change type 'Int' to 'String'"
    )
  }

  def testWidenSingletonLiteralType(): Unit = {
    testQuickFix(
      s"""object wrapper {
         |  var value: Int = $CARET"my text"
         |}
         |""".stripMargin,
      s"""object wrapper {
         |  var value: String = $CARET"my text"
         |}
         |""".stripMargin,
      "Change type 'Int' to 'String'"
    )
  }


  def testParenthesizedBody(): Unit = {
    testQuickFix(
      s"""object wrapper {
         |  val string: String = "42"
         |  def value: Int = (${CARET}string)
         |}
         |""".stripMargin,
      s"""object wrapper {
         |  val string: String = "42"
         |  def value: String = (${CARET}string)
         |}
         |""".stripMargin,
      "Change type 'Int' to 'String'"
    )
  }

  def testIfBranch(): Unit = {
    testQuickFix(
      s"""object wrapper {
         |  val flag: Boolean = true
         |  val string: String = "42"
         |  def value: Int = if (flag) ${CARET}string else "fallback"
         |}
         |""".stripMargin,
      s"""object wrapper {
         |  val flag: Boolean = true
         |  val string: String = "42"
         |  def value: String = if (flag) ${CARET}string else "fallback"
         |}
         |""".stripMargin,
      "Change type 'Int' to 'String'"
    )
  }

  def testTryBody(): Unit = {
    testQuickFix(
      s"""object wrapper {
         |  val string: String = "42"
         |  def value: Int = try ${CARET}string catch { case _: Throwable => "fallback" }
         |}
         |""".stripMargin,
      s"""object wrapper {
         |  val string: String = "42"
         |  def value: String = try ${CARET}string catch { case _: Throwable => "fallback" }
         |}
         |""".stripMargin,
      "Change type 'Int' to 'String'"
    )
  }
}

final class ChangeTypeFixTest_Scala2 extends ChangeTypeFixCommonTests {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

final class ChangeTypeFixTest_Scala3 extends ChangeTypeFixCommonTests {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  def testTypeWildcard(): Unit = {
    testQuickFix(
      s"""class Foo[T]
         |
         |object wrapper {
         |  var value: Int = $CARET(??? : Foo[?])
         |}
         |""".stripMargin,
      s"""class Foo[T]
         |
         |object wrapper {
         |  var value: Foo[?] = $CARET(??? : Foo[?])
         |}
         |""".stripMargin,
      "Change type 'Int' to 'Foo[?]'"
    )
  }


  def test_ContextFunctionBody_DoesNotOfferChangeOuterType(): Unit = {
    //language=Scala
    val code =
      s"""object wrapper {
         |  val string: String = "42"
         |  def value: Int ?=> Int = {
         |    string
         |  }
         |}
         |""".stripMargin

    checkTextHasError(code, allowAdditionalHighlights = true)
    checkNotFixable(code, hint => hint.startsWith("Change type 'Int ?=> Int'"))
  }
}

object ChangeTypeFixTestBase {
  private[quickfix] val DescriptionRegex = "Expression of type .*? doesn't conform to expected type .*?".r
}
