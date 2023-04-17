package org.jetbrains.plugins.scala.editor

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter


package object documentationProvider {

  private[documentationProvider]
  type StringBuilder = java.lang.StringBuilder

  private[documentationProvider]
  def newStringBuilder: StringBuilder = new java.lang.StringBuilder

  private[documentationProvider]
  implicit class StringBuilderOps(private val delegate: java.lang.StringBuilder) extends AnyVal {
    def result: String = delegate.toString
    def isEmpty: Boolean = delegate.length == 0
  }

  private[documentationProvider]
  implicit class HtmlBuilderWrapper(private val delegate: StringBuilder) extends AnyVal {
    def appendKeyword(word: String): StringBuilder = {
      val textAttributes = EditorColorsManager.getInstance.getGlobalScheme.getAttributes(DefaultHighlighter.KEYWORD)
      HtmlSyntaxInfoUtil.appendStyledSpan(delegate, textAttributes, word, 1.0f)
      delegate
    }
  }
}