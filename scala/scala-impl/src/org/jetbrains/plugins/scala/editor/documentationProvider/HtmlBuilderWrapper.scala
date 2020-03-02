package org.jetbrains.plugins.scala.editor.documentationProvider

private class HtmlBuilderWrapper(delegate: StringBuilder) {

  def this() =
    this(new StringBuilder(""))

  def append(txt: String): HtmlBuilderWrapper = {
    delegate.append(txt)
    this
  }

  def append(any: Any): HtmlBuilderWrapper = {
    delegate.append(any)
    this
  }

  def appendNl(): Unit = append("\n")

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

  def withHtmlMarkup(inner: => Unit): Unit =
    html {
      body {
        inner
      }
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

  def result(): String = delegate.result()
}