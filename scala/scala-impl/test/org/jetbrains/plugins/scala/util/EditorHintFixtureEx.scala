package org.jetbrains.plugins.scala.util

import com.intellij.openapi.Disposable
import com.intellij.testFramework.fixtures.EditorHintFixture
import org.jetbrains.annotations.Nullable
import org.junit.Assert.assertNotNull

class EditorHintFixtureEx(parentDisposable: Disposable) extends EditorHintFixture(parentDisposable) {
  val BodyStartTag = "<body>"
  val BodyEndTag = "</body>"

  val HtmlStartTag = "<html>"
  val HtmlEndTag = "</html>"

  /**
   * The whole hint text can have a lot of boilerplate HTML code,
   * added by [[com.intellij.codeInsight.hint.HintUtil.createInformationLabel]] which is used when showing the hint test.
   * During the tests we are mostly interested in the generated body text.
   */
  def getCurrentHintBodyText: String = {
    val text = super.getCurrentHintText
    assertNotNull("Current hint is missing", text)

    val bodyStart = text.indexOf(BodyStartTag)
    val bodyEnd = text.indexOf(BodyEndTag, bodyStart)
    if (bodyStart >= 0 || bodyEnd >= 0)
      text.substring(bodyStart + BodyStartTag.length, bodyEnd)
    else
      throw new AssertionError(s"Can't find $BodyStartTag html content in text:\n$text")
  }

  /**
   * Get current showing hint text or `null` if no hint is present, based on [[getCurrentHintText]].
   *
   * @param stripHtml if `true`, remove `<html><body>` prefix and `</body></html>` suffix if present
   */
  @Nullable
  def getCurrentHintText(stripHtml: Boolean): String = {
    val text = super.getCurrentHintText
    if (!stripHtml || text == null)
      text
    else {
      text
        .stripPrefix(HtmlStartTag)
        .stripSuffix(HtmlEndTag)
        .stripPrefix(BodyStartTag)
        .stripSuffix(BodyEndTag)
    }
  }
}
