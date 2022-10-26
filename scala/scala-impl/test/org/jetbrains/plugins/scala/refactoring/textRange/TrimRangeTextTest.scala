package org.jetbrains.plugins.scala.refactoring.textRange

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.junit.Assert.assertEquals
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.trimRangeText

class TrimRangeTextTest extends SimpleTestCase {

  def testEmpty(): Unit = {
    val text = ""
    val startOffset = 0
    val endOffset = 0
    val (rangeText, startTrimmed, endTrimmed) = trimRangeText(text, startOffset, endOffset)
    assertEquals(text, rangeText)
    assertEquals(startOffset, startTrimmed)
    assertEquals(endOffset, endTrimmed)
  }

  def testWhitespacesOnly(): Unit = {
    val text =
      """

        """
    val startOffset = 0
    val endOffset = text.length
    val (rangeText, startTrimmed, endTrimmed) = trimRangeText(text, startOffset, endOffset)
    assertEquals("", rangeText)
    assertEquals(startOffset, startTrimmed)
    assertEquals(startTrimmed, endTrimmed)
  }

  def testProperHighlight(): Unit = {
    val text = "List(1, 2, 3).map(inc).sum + 1"
    val startOffset = 0
    val endOffset = text.length
    val (rangeText, startTrimmed, endTrimmed) = trimRangeText(text, startOffset, endOffset)
    assertEquals(text, rangeText)
    assertEquals(startOffset, startTrimmed)
    assertEquals(endOffset, endTrimmed)
  }

  def testLeftTrim(): Unit = {
    val text = " List(1, 2, 3).map(inc).sum + 1"
    val startOffset = 0
    val endOffset = text.length
    val (rangeText, startTrimmed, endTrimmed) = trimRangeText(text, startOffset, endOffset)
    assertEquals("List(1, 2, 3).map(inc).sum + 1", rangeText)
    assertEquals(1, startTrimmed)
    assertEquals(endOffset, endTrimmed)
  }

  def testRightTrim(): Unit = {
    val text = "List(1, 2, 3).map(inc).sum + 1 "
    val startOffset = 0
    val endOffset = text.length
    val (rangeText, startTrimmed, endTrimmed) = trimRangeText(text, startOffset, endOffset)
    assertEquals("List(1, 2, 3).map(inc).sum + 1", rangeText)
    assertEquals(startOffset, startTrimmed)
    assertEquals(endOffset - 1, endTrimmed)
  }

  def testBothTrim(): Unit = {
    val text = " List(1, 2, 3).map(inc).sum + 1 "
    val startOffset = 0
    val endOffset = text.length
    val (rangeText, startTrimmed, endTrimmed) = trimRangeText(text, startOffset, endOffset)
    assertEquals("List(1, 2, 3).map(inc).sum + 1", rangeText)
    assertEquals(1, startTrimmed)
    assertEquals(endOffset - 1, endTrimmed)
  }

  def testNewLines(): Unit = {
    val text =
      """

           List(1, 2, 3).map(inc).sum + 1
           """
    val (rangeText, _, _) = trimRangeText(text, 0, text.length)
    assertEquals("List(1, 2, 3).map(inc).sum + 1", rangeText)
  }

  def testOneCharacterLongName(): Unit = {
    val text = " a "
    val (rangeText, startTrimmed, endTrimmed) = trimRangeText(text, 0, text.length)
    assertEquals("a", rangeText)
    assertEquals(1, startTrimmed)
    assertEquals(2, endTrimmed)
  }
}
