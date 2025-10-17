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
         | It also contains a code reference to <a href="psi_element://scala.collection.immutable.List"><code>List</code></a>.</p><h2>Code Examples</h2><p>Here's a simple code example:</p><pre><code>
         | <span style="color:#000080">val</span> <span style="color:#000000">numbers</span> = List(<span style="color:#0000ff">1</span>, <span style="color:#0000ff">2</span>, <span style="color:#0000ff">3</span>, <span style="color:#0000ff">4</span>, <span style="color:#0000ff">5</span>)
         | <span style="color:#000080">val</span> <span style="color:#000000">doubled</span> = numbers.map(_ * <span style="color:#0000ff">2</span>)</code></pre><h2>Parameters</h2></div><table class='sections'><tr><td valign='top' class='section'><p>Params:</td><td valign='top'><code>input</code> &ndash; <span><span></span><span></span>The input string to process</span><p><code>count</code> &ndash; <span><span></span><span></span>The number of times to process<h2>Return Value</h2></span></td><tr><td valign='top' class='section'><p>Returns:</td><td valign='top'><span><span></span>An <a href="psi_element://scala.Option"><code>Option</code></a> containing the result<h2>Exceptions</h2></span></td><tr><td valign='top' class='section'><p>Throws:</td><td valign='top'><a href="psi_element://IllegalArgumentException"><code>IllegalArgumentException</code></a> &ndash; <span><span></span><span></span>if input is empty</span><p><a href="psi_element://NullPointerException"><code>NullPointerException</code></a> &ndash; <span><span></span><span></span>if input is null</span></td></table>
         |""".stripMargin,
      HtmlSpacesComparisonMode.DontIgnore,
    )
  }
}
