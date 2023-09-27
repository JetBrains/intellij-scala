package org.jetbrains.plugins.scala.editor

import com.intellij.openapi.editor.colors.{EditorColorsManager, TextAttributesKey}
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter


package object documentationProvider {

  private[documentationProvider]
  implicit class HtmlBuilderWrapper(private val delegate: StringBuilder) extends AnyVal {
    def appendKeyword(word: String): StringBuilder = appendAs(word, DefaultHighlighter.KEYWORD)

    def appendAs(word: String, textAttributesKey: TextAttributesKey): StringBuilder = {
      val textAttributes = EditorColorsManager.getInstance.getGlobalScheme.getAttributes(textAttributesKey)
      HtmlSyntaxInfoUtil.appendStyledSpan(delegate.underlying, textAttributes, word, 1.0f)
      delegate
    }
  }

  //"\n" separator works as well as `<br>` tag because definition section is located inside `<pre>` tag (which preserves new lines)
  //however "\n" is more convenient to us in expected test data
  val NewLineSeparatorInDefinitionSection = "\n"
}