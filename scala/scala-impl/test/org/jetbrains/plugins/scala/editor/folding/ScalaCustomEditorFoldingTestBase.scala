package org.jetbrains.plugins.scala.editor.folding

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertFalse

abstract class ScalaCustomEditorFoldingTestBase extends ScalaEditorFoldingTestBase {
  protected def customFoldingStart(description: String): String
  protected def customFoldingEnd: String

  def testCustomFolding(): Unit = {
    // NOTE: `def bar` is considered multiline (`isMultiline(node) == true`)
    // because it has a comment attached to it on a previous line
    // this seems like a little bug

    @Language("Scala")
    val text =
      s"""
         |object Test $BLOCK_ST{
         |
         |  ${ST("first custom folding")}//${customFoldingStart("first custom folding")}
         |  case class Foo(i: Int, s: String)
         |
         |  def foo(i: Int, s: String) = Foo(i, s)
         |  //$customFoldingEnd$END
         |
         |  ${ST("second custom folding")}//${customFoldingStart("second custom folding")}
         |  def bar(): Unit = $BLOCK_ST{}$END
         |
         |  def baz(i: Int): String = " " * i
         |  //$customFoldingEnd$END
         |
         |  class A
         |}$END
         |""".stripMargin

    // custom foldings are added first, so we need to sort the foldings
    genericCheckRegions(text, sortFoldings = true)
  }

  def testCustomFolding2(): Unit = {
    @Language("Scala")
    val text =
      s"""
         |class Test $BLOCK_ST{
         |  ${ST("My private fields")}//${customFoldingStart("My private fields")}
         |  private var x: Int = ${DOTS_ST}_$END
         |  private var y: String = _
         |  //$customFoldingEnd$END
         |
         |  ${ST("My methods")}//${customFoldingStart("My methods")}
         |  private def doSomething(): Unit = $BLOCK_ST{
         |    val s = new StringBuilder()
         |    ${ST("My code")}//${customFoldingStart("My code")}
         |    s.append("a")
         |    s.append("b")
         |    //$customFoldingEnd$END
         |    println(s.toString())
         |  }$END
         |  //$customFoldingEnd$END
         |}$END
         |""".stripMargin

    genericCheckRegions(text, sortFoldings = true)
  }

  def testCustomFoldingsIntersectingWithLanguageOnes(): Unit = {
    // dont fold line comments
    @Language("Scala")
    val text =
    s"""
       |class Foo $BLOCK_ST{
       |//*********************************************
       |${ST("Some")}//${customFoldingStart("Some")}
       |//*********************************************
       |
       |  val x = 1
       |
       |//*********************************************
       |//$customFoldingEnd$END
       |//*********************************************
       |}$END
       |""".stripMargin

    genericCheckRegions(text, sortFoldings = true)
  }

  def testCustomFoldingsIntersectingWithLineCommentFoldings(): Unit = {
    // NOTE: `val x` is considered multiline (`isMultiline(node) == true`)
    // because it has a comment attached to it on a previous line
    // this seems like a little bug

    @Language("Scala")
    val text =
      s"""
         |class Foo $BLOCK_ST{
         |$COMMENT_ST// 0
         |// 1$END
         |${ST("Some")}//${customFoldingStart("Some")}
         |$COMMENT_ST// 2
         |// 3 next empty line is significant$END
         |
         |// non-dangling
         |  val x = ${DOTS_ST}1$END
         |$COMMENT_ST// 4
         |// 5$END
         |//$customFoldingEnd$END
         |$COMMENT_ST// 6
         |// 7$END
         |}$END
         |""".stripMargin

    genericCheckRegions(text, sortFoldings = true)
  }

  def testCustomFoldingsHaveHigherPrecedenceThanRegularLineComments(): Unit = {
    @Language("Scala")
    val text =
      s"""
         |class A $BLOCK_ST{
         |    ${ST("Some")}//${customFoldingStart("Some")}
         |    //
         |    //$customFoldingEnd$END
         |    @deprecated
         |    def m(): Unit = $BLOCK_ST{}$END
         |}$END
         |""".stripMargin

    genericCheckRegions(text, sortFoldings = true)
  }

  def testCustomEditorFoldingsAreCollapsed(): Unit = runWithModifiedFoldingSettings { settings =>
    def codeExample(isCollapsedByDefault: Boolean): String = {
      val collapsedMarker = if (isCollapsedByDefault) COLLAPSED_BY_DEFAULT_MARKER else ""
      s"""
         |${ST("some region")}$collapsedMarker//${customFoldingStart("some region")}
         |// Some comment in the region
         |//$customFoldingEnd$END
         |
         |class A
         |""".stripMargin
    }

    assertFalse(settings.COLLAPSE_CUSTOM_FOLDING_REGIONS)
    genericCheckRegions(codeExample(false))

    settings.COLLAPSE_CUSTOM_FOLDING_REGIONS = true
    genericCheckRegions(codeExample(true))
  }

  def testCustomFoldingsAreNotMixed(): Unit
}
