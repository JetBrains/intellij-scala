package org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3

import com.intellij.testFramework.EditorTestUtil.{CARET_TAG => Caret}
import junit.framework.TestCase
import org.jetbrains.plugins.scala.extensions.StringExt
import org.junit.Assert.{assertEquals, fail}

private[scala3] object TestStringUtils {

  implicit class StringOps(private val text: String) extends AnyVal {

    /** @param count number of characters to remove */
    def removeBeforeCaret(count: Int): String = {
      val caretIndex = text.indexOf(Caret)
      if (caretIndex < 0) {
        fail(s"no caret found in code: $text")
      }
      removeBefore(count, caretIndex)
    }

    def removeBefore(count: Int, index: Int): String =
      text.substring(0, index - count) + text.substring(index)

    def removeRange(from: Int, to: Int): String =
      text.substring(0, from) + text.substring(to)

    def insertStringBeforeCaret(inserted: String): String = {
      val caretIndex = text.indexOf(Caret)
      if (caretIndex < 0) {
        fail(s"no caret found in code: $text")
      }
      insertString(caretIndex, inserted)
    }

    //noinspection MutatorLikeMethodIsParameterless
    def removeSpacesAfterCaret: String = {
      val caretIndex = text.indexOf(Caret)
      if (caretIndex < 0) {
        fail(s"no caret found in code: $text")
      }
      val caretEndIndex = caretIndex + Caret.length
      val nonWsIndex = text.indexWhere(_ != ' ', caretEndIndex)
      if (nonWsIndex == -1)
        text
      else
        text.removeRange(caretEndIndex, nonWsIndex)
    }

    def insertString(index: Int, inserted: String): String =
      text.substring(0, index) + inserted + text.substring(index)

    def lineWithCaret: String = {
      val caretIndex = text.indexOf(Caret)
      val lineStart = text.lastIndexOf('\n', caretIndex) + 1
      val lineEnd = text.indexOf('\n', caretIndex) match {
        case -1  => text.length
        case idx => idx
      }
      text.substring(lineStart, lineEnd)
    }
  }
}

private[scala3] class TestStringUtilsTest extends TestCase {

  import TestStringUtils.StringOps

  private def doRemoveSpacesAfterCaretTest(before: String, after: String): Unit = {
    val actual = before.withNormalizedSeparator.removeSpacesAfterCaret
    assertEquals(after.withNormalizedSeparator, actual)
  }

  private def doRemoveSpacesAfterCaretTest(before: String): Unit = {
    doRemoveSpacesAfterCaretTest(before, before)
  }

  def test_removeSpacesAfterCaret(): Unit = {
    doRemoveSpacesAfterCaretTest(s"""$Caret""".stripMargin)
    doRemoveSpacesAfterCaretTest(s""" $Caret""".stripMargin)
    doRemoveSpacesAfterCaretTest(s"""    $Caret""".stripMargin)

    doRemoveSpacesAfterCaretTest(
      s"""    ${""}
         |  ${""}
         |
         |$Caret
         |
         |""".stripMargin
    )

    doRemoveSpacesAfterCaretTest(
      s"""    ${""}
         |  ${""}
         |
         |   $Caret
         |
         |""".stripMargin
    )
    doRemoveSpacesAfterCaretTest(
      s"""aaa
         |   ${Caret}bbb
         |ccc
         |""".stripMargin
    )
    doRemoveSpacesAfterCaretTest(
      s"""aaa
         |   $Caret    bbb
         |ccc
         |""".stripMargin,
      s"""aaa
         |   ${Caret}bbb
         |ccc
         |""".stripMargin
    )
  }
}
