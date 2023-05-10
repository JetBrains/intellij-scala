package org.jetbrains.plugins.scala.editor

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter


package object documentationProvider {

  private[documentationProvider]
  implicit class HtmlBuilderWrapper(private val delegate: StringBuilder) extends AnyVal {
    def appendKeyword(word: String): StringBuilder = {
      val textAttributes = EditorColorsManager.getInstance.getGlobalScheme.getAttributes(DefaultHighlighter.KEYWORD)
      HtmlSyntaxInfoUtil.appendStyledSpan(delegate.underlying, textAttributes, word, 1.0f)
      delegate
    }
  }
}