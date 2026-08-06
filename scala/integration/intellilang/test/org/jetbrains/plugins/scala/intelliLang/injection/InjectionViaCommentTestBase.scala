package org.jetbrains.plugins.scala.intelliLang.injection

import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.intelliLang.injection.ScalaInjectionTestFixture.{ExpectedInjection, ShredInfo}

abstract class InjectionViaCommentTestBase extends ScalaLanguageInjectionTestBase {
  protected def doInjectedViaCommentTest(
    languageId: String,
    text: String,
    expectedText: String,
    expectedShreds: Seq[ShredInfo] = null
  ): Unit = {
    val textWithComment =
      s"""//language=$languageId
         |$text
         |""".stripMargin.replace("'''", "\"\"\"")
    val expectedInjection = ExpectedInjection(
      expectedText.withNormalizedSeparator,
      languageId,
      Option(expectedShreds)
    )
    scalaInjectionTestFixture.doTest(textWithComment, expectedInjection)
  }
}
