package org.jetbrains.plugins.scala.editor.documentationProvider

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.editor.documentationProvider.util.ScalaDocumentationsBodySectionTesting

class Scala3DocumentationProviderTest_Markdown
  extends ScalaDocumentationProviderTestBase
    with ScalaDocumentationsBodySectionTesting {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3


  def testAllFeaturesCombined(): Unit = {
    val fileContent =
      s"""
         |/**
         | * # Complete Documentation Example
         | *
         | * This is a paragraph with **bold** and *italic* text.
         | * It also contains a code reference to [[scala.collection.immutable.List]].
         | *
         | * ## Code Examples
         | *
         | * Here's a simple code example:
         | * {{{
         | * val numbers = List(1, 2, 3, 4, 5)
         | * val doubled = numbers.map(_ * 2)
         | * }}}
         | *
         | * ## Parameters
         | *
         | * @param input The input string to process
         | * @param count The number of times to process
         | *
         | * ## Return Value
         | *
         | * @return An [[scala.Option]] containing the result
         | *
         | * ## Exceptions
         | *
         | * @throws IllegalArgumentException if input is empty
         | * @throws NullPointerException if input is null
         | */
         |class ${|}Foo
         |""".stripMargin

    doGenerateRenderedDocBodyTest(
      fileContent,
      s"""
         |<div class='content'><h1>Complete Documentation Example</h1><p>This is a paragraph with <strong>bold</strong> and <em>italic</em> text.
         |It also contains a code reference to <a href="psi_element://scala.collection.immutable.List"><code>scala.collection.immutable.List</code></a>.</p><h2>Code Examples</h2><p>Here's a simple code example:</p><pre><code>
         |<span style="color:#000080">val</span> <span style="color:#000000">numbers</span> = List(<span style="color:#0000ff">1</span>, <span style="color:#0000ff">2</span>, <span style="color:#0000ff">3</span>, <span style="color:#0000ff">4</span>, <span style="color:#0000ff">5</span>)
         |<span style="color:#000080">val</span> <span style="color:#000000">doubled</span> = numbers.map(_ * <span style="color:#0000ff">2</span>)</code></pre><h2>Parameters</h2></div><table class='sections'><tr><td valign='top' class='section'><p>Params:</td><td valign='top'><code>input</code> &ndash; <span><span></span><span></span>The input string to process</span><p><code>count</code> &ndash; <span><span></span><span></span>The number of times to process<h2>Return Value</h2></span></td><tr><td valign='top' class='section'><p>Returns:</td><td valign='top'><span><span></span>An <a href="psi_element://scala.Option"><code>scala.Option</code></a> containing the result<h2>Exceptions</h2></span></td><tr><td valign='top' class='section'><p>Throws:</td><td valign='top'><a href="psi_element://IllegalArgumentException"><code>IllegalArgumentException</code></a> &ndash; <span><span></span><span></span>if input is empty</span><p><a href="psi_element://NullPointerException"><code>NullPointerException</code></a> &ndash; <span><span></span><span></span>if input is null</span></td></table>
         |""".stripMargin,
      HtmlSpacesComparisonMode.DontIgnore,
    )
  }

  def testQuoteOnly(): Unit = {

    doGenerateRenderedDocBodyTest(
      s"""
         |/**
         | * > quote only
         | */
         |class ${|}Foo
         |""".stripMargin,
      s"""
         |<div class='content'><blockquote><p>quote only</p></blockquote></div>
         |""".stripMargin,
      HtmlSpacesComparisonMode.DontIgnore,
    )
  }

  def testCodeBlockOnly(): Unit =
    doGenerateRenderedDocBodyTest(
      s"""
         |/**
         | * ```
         | * code
         | * ```
         | */
         |class ${|}Foo
         |""".stripMargin,
      s"""
         |<div class='content'><pre><code>
         |code</code></pre></div>
         |""".stripMargin,
      HtmlSpacesComparisonMode.DontIgnore,
    )

  def testOldCodeBlockOnly(): Unit =
    doGenerateRenderedDocBodyTest(
      s"""
         |/**
         | * {{{
         | * code
         | * }}}
         | */
         |class ${|}Foo
         |""".stripMargin,
      s"""
         |<div class='content'><pre><code>
         |code</code></pre></div>
         |""".stripMargin,
      HtmlSpacesComparisonMode.DontIgnore,
    )

  def testHeaderOnly(): Unit =
    doGenerateRenderedDocBodyTest(
      s"""
         |/**
         | * # Header
         | */
         |class ${|}Foo
         |""".stripMargin,
      s"""
         |<div class='content'><h1>Header</h1></div>
         |""".stripMargin,
      HtmlSpacesComparisonMode.DontIgnore,
    )

  def testHeaders(): Unit =
    doGenerateRenderedDocBodyTest(
      s"""
         |/**
         | * # a
         | * ## b
         | * ### c
         | * #### d
         | * ##### e
         | * ###### f
         | */
         |class ${|}Foo
         |""".stripMargin,
      s"""
         |<div class='content'><h1>a</h1><h2>b</h2><h3>c</h3><h4>d</h4><h5>e</h5><h6>f</h6></div>
         |""".stripMargin,
      HtmlSpacesComparisonMode.DontIgnore,
    )

  def testNestedQuotes(): Unit =
    doGenerateRenderedDocBodyTest(
      s"""
         |/**
         | * > a
         | * > > b
         | * > > > c
         | * >
         | * > d
         | * > > > e
         | * > >
         | * > > f
         | */
         |class ${|}Foo
         |""".stripMargin,
      s"""
         |<div class='content'><blockquote><p>a</p><blockquote><p>b</p><blockquote><p>c</p></blockquote></blockquote><p>d</p><blockquote><blockquote><p>e</p></blockquote><p>f</p></blockquote></blockquote></div>
         |""".stripMargin,
      HtmlSpacesComparisonMode.DontIgnore,
    )

  def testReturn(): Unit =
    doGenerateRenderedDocBodyTest(
      s"""
         |/**
         | * @return A first line
         | *         then a second line that is actually on the same line
         | *         # not a header
         | *
         | *         Then a new paragraph.
         | *         > abc
         | *         > > def
         | *
         | * > actually a quote
         | * >> nested quote
         | *
         | * # a header
         | *
         | * ```
         | * code block
         | * ```
         | */
         |class ${|}Foo
         |""".stripMargin,
      s"""
         |<table class='sections'><tr><td valign='top' class='section'><p>Returns:</td><td valign='top'><span><span></span>A first line
         |        then a second line that is actually on the same line
         |        # not a header<p>Then a new paragraph.
         |        &gt; abc
         |        &gt; &gt; def</p><blockquote><p>actually a quote</p><blockquote><p>nested quote</p></blockquote></blockquote><h1>a header</h1><pre><code>
         |code block</code></pre></span></td></table>
         |""".stripMargin,
      HtmlSpacesComparisonMode.DontIgnore,
    )

  def test_return_quote(): Unit =
    doGenerateRenderedDocBodyTest(
      s"""
         |/**
         | * @return > a quote
         | */
         |class ${|}Foo
         |""".stripMargin,
      """
        |<table class='sections'><tr><td valign='top' class='section'><p>Returns:</td><td valign='top'><span><span></span><blockquote>a quote</blockquote></span></td></table>
        |""".stripMargin,
      HtmlSpacesComparisonMode.DontIgnore,
    )

  def test_return_header(): Unit =
    doGenerateRenderedDocBodyTest(
      s"""
         |/**
         | * @return # a header
         | */
         |class ${|}Foo
         |""".stripMargin,
      """
        |<table class='sections'><tr><td valign='top' class='section'><p>Returns:</td><td valign='top'><span><span></span><h1>a header</h1></span></td></table>
        |""".stripMargin,
      HtmlSpacesComparisonMode.DontIgnore,
    )

  def test_links(): Unit =
    doGenerateRenderedDocBodyTest(
      s"""
         |/**
         | * [[Foo]]
         | *
         | * [[http://example.com/Foo]]
         | */
         |class ${|}Foo
         |""".stripMargin,
      """
        |<div class='content'><p><a href="psi_element://Foo"><code>Foo</code></a></p><p><a href="http://example.com/Foo">http://example.com/Foo</a></p></div>
        |""".stripMargin,
      HtmlSpacesComparisonMode.DontIgnore,
    )

  def test_autolink(): Unit =
    doGenerateRenderedDocBodyTest(
      s"""
         |/**
         | * autolink: http://example.com/Foo
         | * autolink in bold: **http://example.com/Foo**
         | * no autolink: [[http://example.com/Foo]]
         | */
         |class ${|}Foo
         |""".stripMargin,
      """
        |<div class='content'><p>autolink: <a href="http://example.com/Foo">http://example.com/Foo</a>
        |autolink in bold: <strong><a href="http://example.com/Foo">http://example.com/Foo</a></strong>
        |no autolink: <a href="http://example.com/Foo">http://example.com/Foo</a></p></div>
        |""".stripMargin,
      HtmlSpacesComparisonMode.DontIgnore,
    )

  def test_strikethrough(): Unit =
    doGenerateRenderedDocBodyTest(
      s"""
         |/**
         | * ~~strikethrough~~
         | */
         |class ${|}Foo
         |""".stripMargin,
      "<div class='content'><p><strike>strikethrough</strike></p></div>",
      HtmlSpacesComparisonMode.DontIgnore,
    )
}
