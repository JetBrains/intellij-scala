package org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3

import com.intellij.testFramework.EditorTestUtil
import junit.framework.TestCase
import org.jetbrains.plugins.scala.extensions.StringExt
import org.junit.Assert.{assertEquals, fail}

private[editor] object TestIndentUtils {

  private val Caret = EditorTestUtil.CARET_TAG

  def calcIndentBefore(text: String, offset: Int): Int =
    text.view
      .slice(0, offset).reverseIterator
      .takeWhile(_ == ' ')
      .size

  def calcIndentAtSameLine(text: String, offset: Int): Int = {
    text.view
      .slice(0, offset).reverseIterator
      .takeWhile(_ == ' ')
      .size
  }

  def addIndentAfterAllNewLines(injectedCode: String, indentSize: Int): String = {
    val indentStr = " " * indentSize
    injectedCode.replace("\n", "\n" + indentStr)
  }

  def addIndentToAllLines(injectedCode: String, indentSize: Int): String = {
    val indentStr = " " * indentSize
    injectedCode.linesIterator.map(indentStr + _).mkString("\n")
  }

  /** See [[TestIndentUtilsTest]] for the example */
  def injectCodeWithIndentAdjust(injectedCode: String, contextCode: String, placeholder: String): String = {
    val placeholderIdx = contextCode.indexOf(placeholder)
    val baseIndent = TestIndentUtils.calcLineIndent(contextCode, placeholderIdx)
    val injectedCodeIndented = TestIndentUtils.addIndentAfterAllNewLines(injectedCode, baseIndent)
    contextCode.replaceFirst(placeholder, injectedCodeIndented)
  }

  def calcLineAtCaretIndent(code: String): Int = {
    val caretOffset = code.indexOf(Caret)
    if (caretOffset < 0) {
      fail(s"no caret found in code: $code")
    }
    calcLineIndent(code, caretOffset)
  }

  def calcLineIndent(code: String, offset: Int): Int = {
    val newLineIdx = code.lastIndexOf('\n', offset) + 1
    val codeIdx = code.indexWhere(_ != ' ', newLineIdx)
    codeIdx - newLineIdx
  }
}

private[scala3] class TestIndentUtilsTest extends TestCase {

  private val Caret = "<caret>"

  private def assertIndentSize(expectedIndentSize: Int, text0: String): Unit = {
    val text = text0.withNormalizedSeparator
    val caretOffset = text.indexOf(Caret)
    val actualIndentSize = TestIndentUtils.calcIndentBefore(text, caretOffset)
    assertEquals(expectedIndentSize, actualIndentSize)
  }

  def test_calcIndentBefore_0_0(): Unit = assertIndentSize(0, s"""$Caret""")
  def test_calcIndentBefore_0_1(): Unit = assertIndentSize(1, s""" $Caret""")
  def test_calcIndentBefore_0_2(): Unit = assertIndentSize(2, s"""  $Caret""")
  def test_calcIndentBefore_0_3(): Unit = assertIndentSize(3, s"""   $Caret""")

  def test_calcIndentBefore_1_0(): Unit = assertIndentSize(0, s"""$Caret    """)
  def test_calcIndentBefore_1_1(): Unit = assertIndentSize(1, s""" $Caret    """)
  def test_calcIndentBefore_1_2(): Unit = assertIndentSize(2, s"""  $Caret    """)
  def test_calcIndentBefore_1_3(): Unit = assertIndentSize(3, s"""   $Caret    """)

  def test_calcIndentBefore_2_0(): Unit = assertIndentSize(0, s"""aaaa\n$Caret""")
  def test_calcIndentBefore_2_1(): Unit = assertIndentSize(1, s"""aaaa\n $Caret""")
  def test_calcIndentBefore_2_2(): Unit = assertIndentSize(2, s"""aaaa\n  $Caret""")
  def test_calcIndentBefore_2_3(): Unit = assertIndentSize(3, s"""aaaa\n   $Caret""")

  def test_calcIndentBefore_3_0(): Unit = assertIndentSize(0, s"""   aaaa   \n\r\n\r   b    \n\n$Caret""")
  def test_calcIndentBefore_3_1(): Unit = assertIndentSize(1, s"""   aaaa   \n\r\n\r   b    \n\n $Caret""")
  def test_calcIndentBefore_3_2(): Unit = assertIndentSize(2, s"""   aaaa   \n\r\n\r   b    \n\n  $Caret""")
  def test_calcIndentBefore_3_3(): Unit = assertIndentSize(3, s"""   aaaa   \n\r\n\r   b    \n\n   $Caret""")

  private def assertIndentedNewLInes(indentSize: Int, textBefore: String, expectedTextAfter: String): Unit = {
    val actualTextAfter = TestIndentUtils.addIndentAfterAllNewLines(
      textBefore,
      indentSize
    )
    assertEquals(
      expectedTextAfter,
      actualTextAfter
    )
  }

  def test_addIndentAfterAllNewLines_0(): Unit = assertIndentedNewLInes(0, "aaa\nbbb\nccc", "aaa\nbbb\nccc")
  def test_addIndentAfterAllNewLines_1(): Unit = assertIndentedNewLInes(1, "aaa\nbbb\nccc", "aaa\n bbb\n ccc")
  def test_addIndentAfterAllNewLines_2(): Unit = assertIndentedNewLInes(5, "aaa\nbbb\nccc", "aaa\n     bbb\n     ccc")
  def test_addIndentAfterAllNewLines_3(): Unit = assertIndentedNewLInes(5, "\naaa\nbbb\nccc", "\n     aaa\n     bbb\n     ccc")

  def testInjectCodeToContext_0(): Unit = {
    val injected =
      """def foo = {
        |  var x = 1
        |  var y = 2
        |  x + y
        |}
        |""".stripMargin
    val context = s"""$Caret"""
    val expected =
      """def foo = {
        |  var x = 1
        |  var y = 2
        |  x + y
        |}
        |""".stripMargin
    val actual = TestIndentUtils.injectCodeWithIndentAdjust(injected, context, Caret)
    assertEquals(expected, actual)
  }

  def testInjectCodeToContext_1(): Unit = {
    val injected =
      """def foo = {
        |  var x = 1
        |  var y = 2
        |  x + y
        |}""".stripMargin
    val context =
      s"""
         |  $Caret
         |""".stripMargin
    val expected =
      """
        |  def foo = {
        |    var x = 1
        |    var y = 2
        |    x + y
        |  }
        |""".stripMargin
    val actual = TestIndentUtils.injectCodeWithIndentAdjust(injected, context, Caret)
    assertEquals(expected, actual)
  }

  def testInjectCodeToContext_2(): Unit = {
    val injected =
      """def foo = {
        |  var x = 1
        |  var y = 2
        |  x + y
        |}""".stripMargin
    val context =
      s"""class A {
         |  class B {
         |    $Caret
         |  }
         |}
         |""".stripMargin
    val expected =
      """class A {
        |  class B {
        |    def foo = {
        |      var x = 1
        |      var y = 2
        |      x + y
        |    }
        |  }
        |}
        |""".stripMargin
    val actual = TestIndentUtils.injectCodeWithIndentAdjust(injected, context, Caret)
    assertEquals(expected, actual)
  }

  def testInjectCodeToContext_3(): Unit = {
    val injected =
      """
        |  1
        |  2
        |  3""".stripMargin
    val context =
      s"""def foo =
         |  def bar = $Caret
         |""".stripMargin
    val expected =
      s"""def foo =
        |  def bar = ${""}
        |    1
        |    2
        |    3
        |""".stripMargin
    val actual = TestIndentUtils.injectCodeWithIndentAdjust(injected, context, Caret)
    assertEquals(expected, actual)
  }

  def test_calcLineWithCaretIndent_0(): Unit =
    assertEquals(0, TestIndentUtils.calcLineAtCaretIndent(
      """<caret>"""
    ))
  def test_calcLineWithCaretIndent_1(): Unit =
    assertEquals(0, TestIndentUtils.calcLineAtCaretIndent(
      """
        |
        |<caret>""".stripMargin
    ))
  def test_calcLineWithCaretIndent_2(): Unit =
    assertEquals(3, TestIndentUtils.calcLineAtCaretIndent(
      """   <caret>""".stripMargin
    ))
  def test_calcLineWithCaretIndent_3(): Unit =
    assertEquals(3, TestIndentUtils.calcLineAtCaretIndent(
      """
        |   <caret>
        |
        |some code""".stripMargin
    ))

  def test_calcLineWithCaretIndent_4(): Unit =
    assertEquals(3, TestIndentUtils.calcLineAtCaretIndent(
      """
        |   some code before caret <caret>
        |
        |  some code
        |     some code""".stripMargin
    ))

  def test_calcLineWithCaretIndent_5(): Unit =
    assertEquals(3, TestIndentUtils.calcLineAtCaretIndent(
      """some code
        |  some code
        |             some code
        |   some code before caret        <caret>
        |
        |  some code
        |     some code""".stripMargin
    ))
}