package org.jetbrains.plugins.scala.editor

import com.intellij.ide.highlighter.JavaHighlightingColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil


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

    def append(txt: CharSequence): HtmlBuilderWrapper = {
      delegate.append(txt)
      this
    }

    def append(any: AnyRef): HtmlBuilderWrapper = {
      delegate.append(any)
      this
    }

    def length: Int = delegate.length

    def withTag(tag: String)(inner: => Unit): Unit = {
      append(s"<$tag>")
      inner
      append(s"</$tag>")
    }

    def withTag(tag: String, params: Seq[(String, String)])(inner: => Unit): Unit = {
      append(s"<$tag ")
      for ((name, value) <- params) append(name + "=\"" + value + "\"")
      append(">")
      inner
      append(s"</$tag>")
    }

    def html(inner: => Unit): Unit = withTag("html")(inner)
    def body(inner: => Unit): Unit = withTag("body")(inner)
    def pre(inner: => Unit): Unit = withTag("pre")(inner)
    def b(inner: => Unit): Unit = withTag("b")(inner)
    def u(inner: => Unit): Unit = withTag("u")(inner)
    def i(inner: => Unit): Unit = withTag("i")(inner)
    def tt(inner: => Unit): Unit = withTag("tt")(inner)
    def sub(inner: => Unit): Unit = withTag("sub")(inner)
    def sup(inner: => Unit): Unit = withTag("sup")(inner)

    def appendKeyword(word: String): StringBuilder = {
      val textAttributes = EditorColorsManager.getInstance.getGlobalScheme.getAttributes(JavaHighlightingColors.KEYWORD)
      HtmlSyntaxInfoUtil.appendStyledSpan(delegate, textAttributes, word, 1.0f)
      delegate
    }
  }
}