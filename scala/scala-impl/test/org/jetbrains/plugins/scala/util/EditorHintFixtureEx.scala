package org.jetbrains.plugins.scala.util

import com.intellij.openapi.Disposable
import com.intellij.testFramework.fixtures.EditorHintFixture

class EditorHintFixtureEx(parentDisposable: Disposable) extends EditorHintFixture(parentDisposable) {

  /**
   * The whole hint text can have a lot of boilerplate HTML code,
   * added by [[com.intellij.codeInsight.hint.HintUtil.createInformationLabel]] which is used when showing the hint test.
   * During the tests we are mostly interested in the generated body text.
   */
  def getCurrentHintBodyText: String = {
    val text = super.getCurrentHintText

    val BodyStartTag = "<body>"
    val BodyEndTag = "</body>"

    val bodyStart = text.indexOf(BodyStartTag)
    val bodyEnd = text.indexOf(BodyEndTag, bodyStart)
    if (bodyStart >= 0 || bodyEnd >= 0)
      text.substring(bodyStart + BodyStartTag.length, bodyEnd)
    else
      throw new AssertionError(s"Can't find <body> html content in text:\n$text")
  }
}
