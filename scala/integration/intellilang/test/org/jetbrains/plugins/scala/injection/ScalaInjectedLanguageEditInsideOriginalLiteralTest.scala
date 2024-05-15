package org.jetbrains.plugins.scala.injection

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.EditorActionTestBase

/**
 * Tests for editing code inside a string literal with an injected file
 */
class ScalaInjectedLanguageEditInsideOriginalLiteralTest extends EditorActionTestBase {

  import EditorTestUtil.{CARET_TAG => Caret}
  import org.jetbrains.plugins.scala.util.MultilineStringUtil.{MultilineQuotes => Quotes}

  private var scalaInjectionTestFixture: ScalaInjectionTestFixture = _

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_2_13

  override protected def setUp(): Unit = {
    super.setUp()

    scalaInjectionTestFixture = new ScalaInjectionTestFixture(getProject, getFixture)
  }

  private def doEnterTestInInjection(before: String, after: String): Unit = {
    performTest(before, after) { () =>
      scalaInjectionTestFixture.assertHasSomeInjectedLanguageAtCaret()
      performEnterAction()
    }
  }

  private def doTypingTestInInjection(textToType: String, before: String, after: String): Unit = {
    performTest(before, after) { () =>
      scalaInjectionTestFixture.assertHasSomeInjectedLanguageAtCaret()
      performTypingAction(textToType)
    }
  }

  def testInsertMarginCharOnEnterInsideInjectedFileInMultilineString(): Unit = {
    val before =
      s"""val x =
         |  //language=JSON
         |  $Quotes{
         |    |  "a" : 42,$Caret
         |    |  "b" : 23
         |    |}$Quotes.stripMargin
         |""".stripMargin

    val after =
      s"""val x =
         |  //language=JSON
         |  $Quotes{
         |    |  "a" : 42,
         |    |  $Caret
         |    |  "b" : 23
         |    |}$Quotes.stripMargin
         |""".stripMargin

    doEnterTestInInjection(before, after)
  }

  def testInsertMarginCharOnEnterInsideInjectedFileInMultilineStringWithNonDefaultMargin(): Unit = {
    val before =
      s"""val x =
         |  //language=JSON
         |  $Quotes{
         |    #  "a" : 42,$Caret
         |    #  "b" : 23
         |    #}$Quotes.stripMargin('#')
         |""".stripMargin

    val after =
      s"""val x =
         |  //language=JSON
         |  $Quotes{
         |    #  "a" : 42,
         |    #  $Caret
         |    #  "b" : 23
         |    #}$Quotes.stripMargin('#')
         |""".stripMargin

    doEnterTestInInjection(before, after)
  }

  def testInsertMarginCharOnEnterInsideInjectedFileInMultilineInterpolatedString(): Unit = {
    val before =
      s"""val x =
         |  //language=JSON
         |  s$Quotes{
         |     |  "a" : 42,$Caret
         |     |  "b" : 23
         |     |}$Quotes.stripMargin
         |""".stripMargin

    val after =
      s"""val x =
         |  //language=JSON
         |  s$Quotes{
         |     |  "a" : 42,
         |     |  $Caret
         |     |  "b" : 23
         |     |}$Quotes.stripMargin
         |""".stripMargin

    doEnterTestInInjection(before, after)
  }

  def testInsertMarginCharOnEnterInsideInjectedFileInMultilineInterpolatedStringWithNonDefaultMargin(): Unit = {
    val before =
      s"""val x =
         |  //language=JSON
         |  s$Quotes{
         |     #  "a" : 42,$Caret
         |     #  "b" : 23
         |     #}$Quotes.stripMargin('#')
         |""".stripMargin

    val after =
      s"""val x =
         |  //language=JSON
         |  s$Quotes{
         |     #  "a" : 42,
         |     #  $Caret
         |     #  "b" : 23
         |     #}$Quotes.stripMargin('#')
         |""".stripMargin

    doEnterTestInInjection(before, after)
  }

  //SCL-22507
  def testTypeColonAfterInvalidJsonProperty_CommentInjection_Multiline(): Unit = {
    doTypingTestInInjection(
      ":",
      s"""object Example {
         |  //language=JSON
         |  \"\"\"{"a":"aaa", b$CARET  }\"\"\"
         |}
         |""".stripMargin,
      s"""object Example {
         |  //language=JSON
         |  \"\"\"{"a":"aaa", "b": $CARET  }\"\"\"
         |}
         |""".stripMargin
    )
  }

  //SCL-22507
  def testTypeColonAfterInvalidJsonProperty_InterpolatorInjection_Multiline(): Unit = {
    doTypingTestInInjection(
      ":",
      s"""object Example {
         |  //1. Via interpolator
         |  implicit class JsonHelper(val sc: StringContext) extends AnyVal {
         |    def json(args: Any*): String = ???
         |  }
         |  json\"\"\"{"a":"aaa", b$CARET }\"\"\"
         |}
         |""".stripMargin,
      s"""object Example {
         |  //1. Via interpolator
         |  implicit class JsonHelper(val sc: StringContext) extends AnyVal {
         |    def json(args: Any*): String = ???
         |  }
         |  json\"\"\"{"a":"aaa", "b": $CARET }\"\"\"
         |}
         |""".stripMargin
    )
  }

  //SCL-22507
  def testTypeColonAfterInvalidJsonProperty_CommentInjection_OneLine(): Unit = {
    //TODO: ignore IJPL-149605 is fixed (we might need to review our implementation as well)
    return
    doTypingTestInInjection(
      ":",
      s"""object Example {
         |  //language=JSON
         |  "{\\"a\\":\\"aaa\\", b$CARET }"
         |}
         |""".stripMargin,
      s"""object Example {
         |  //language=JSON
         |  "{\\"a\\":\\"aaa\\", \\"b\\": $CARET }"
         |}
         |""".stripMargin
    )
  }

  //SCL-22507
  def testTypeColonAfterInvalidJsonProperty_InterpolatorInjection_OneLine(): Unit = {
    //TODO: patch expected data once IJPL-149605 is fixed
    doTypingTestInInjection(
      ":",
      s"""object Example {
         |  //1. Via interpolator
         |  implicit class JsonHelper(val sc: StringContext) extends AnyVal {
         |    def json(args: Any*): String = ???
         |  }
         |  json"{\\"a\\":\\"aaa\\", b$CARET }"
         |}
         |""".stripMargin,
      s"""object Example {
         |  //1. Via interpolator
         |  implicit class JsonHelper(val sc: StringContext) extends AnyVal {
         |    def json(args: Any*): String = ???
         |  }
         |  json"{\\"a\\":\\"aaa\\", b: $CARET }"
         |}
         |""".stripMargin
    )
  }
}
