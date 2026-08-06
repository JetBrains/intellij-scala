package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.documentation.QuickDocHighlightingHelper.{CODE_BLOCK_PREFIX, CODE_BLOCK_SUFFIX}
import org.jetbrains.plugins.scala.editor.documentationProvider.util.ScalaDocumentationsScalaDocContentTesting

class ScalaDocumentationProviderTest_CodeBlocks
  extends ScalaDocumentationProviderTestBase
    with ScalaDocumentationsScalaDocContentTesting {

  def testRemoveMostCommonIndent_1(): Unit = {
    val fileContent =
      s"""/**
         | * {{{
         | * class Wrapper(val underlying: Int) extends AnyVal {
         | *   def foo: Wrapper = new Wrapper(underlying * 19)
         | * }
         | * }}}
         | **/
         |class ${|}Foo
         |""".stripMargin

    doGenerateDocContentTest(
      fileContent,
      s"""$CODE_BLOCK_PREFIX<span style="color:#000080;font-weight:bold;">class&#32;</span><span style="">Wrapper(</span><span style="color:#000080;font-weight:bold;">val&#32;</span><span style="">underlying:&#32;Int)&#32;</span><span style="color:#000080;font-weight:bold;">extends&#32;</span><span style="">AnyVal&#32;{<br></span><span style="">&#32;&#32;</span><span style="color:#000080;font-weight:bold;">def&#32;</span><span style="">foo:&#32;Wrapper&#32;=&#32;</span><span style="color:#000080;font-weight:bold;">new&#32;</span><span style="">Wrapper(underlying&#32;*&#32;</span><span style="color:#0000ff;">19</span><span style="">)<br></span><span style="">}</span>$CODE_BLOCK_SUFFIX""",
      HtmlSpacesComparisonMode.DontIgnore,
    )
  }

  def testRemoveMostCommonIndent_2(): Unit = {
    val fileContent =
      s"""/**
         | * {{{
         | *    class Wrapper(val underlying: Int) extends AnyVal {
         | *      def foo: Wrapper = new Wrapper(underlying * 19)
         | *    }
         | * }}}
         | **/
         |class ${|}Foo
         |""".stripMargin


    doGenerateDocContentTest(
      fileContent,
      s"""$CODE_BLOCK_PREFIX<span style="color:#000080;font-weight:bold;">class&#32;</span><span style="">Wrapper(</span><span style="color:#000080;font-weight:bold;">val&#32;</span><span style="">underlying:&#32;Int)&#32;</span><span style="color:#000080;font-weight:bold;">extends&#32;</span><span style="">AnyVal&#32;{<br></span><span style="">&#32;&#32;</span><span style="color:#000080;font-weight:bold;">def&#32;</span><span style="">foo:&#32;Wrapper&#32;=&#32;</span><span style="color:#000080;font-weight:bold;">new&#32;</span><span style="">Wrapper(underlying&#32;*&#32;</span><span style="color:#0000ff;">19</span><span style="">)<br></span><span style="">}</span>$CODE_BLOCK_SUFFIX""",
      HtmlSpacesComparisonMode.DontIgnore
    )
  }

  def testEscapeAngledBrackets(): Unit = {
    val fileContent =
      s"""/**
         | * {{{
         | *    1 < 3 && 4 > 3
         | * }}}
         | **/
         |class ${|}Foo
         |""".stripMargin

    doGenerateDocContentTest(
      fileContent,
      s"""$CODE_BLOCK_PREFIX<span style="color:#0000ff;">1&#32;</span><span style="">&lt;&#32;</span><span style="color:#0000ff;">3&#32;</span><span style="">&amp;&amp;&#32;</span><span style="color:#0000ff;">4&#32;</span><span style="">&gt;&#32;</span><span style="color:#0000ff;">3</span>$CODE_BLOCK_SUFFIX""",
    )
  }

  def testDontAddExtraSpaceBeforeCodeBlock(): Unit = {
    val fileContent =
      s"""/**
         | * Example
         | * {{{42}}}
         | *
         | * Example
         | *
         | * {{{42}}}
         | *
         | * Example
         | *
         | *
         | * {{{42}}}
         | **/
         |class ${|}Foo
         |""".stripMargin

    doGenerateDocContentTest(
      fileContent,
      s"""Example
         | $CODE_BLOCK_PREFIX<span style="color:#0000ff;">42</span>$CODE_BLOCK_SUFFIX
         |<p>Example</p>
         |$CODE_BLOCK_PREFIX<span style="color:#0000ff;">42</span>$CODE_BLOCK_SUFFIX
         |<p>Example</p>
         |$CODE_BLOCK_PREFIX<span style="color:#0000ff;">42</span>$CODE_BLOCK_SUFFIX""".stripMargin,
      HtmlSpacesComparisonMode.DontIgnore
    )
  }
}
