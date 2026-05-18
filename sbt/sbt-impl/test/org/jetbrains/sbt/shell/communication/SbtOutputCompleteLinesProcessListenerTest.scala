package org.jetbrains.sbt.shell.communication

import org.jetbrains.plugins.scala.extensions.StringExt
import org.junit.Assert.assertEquals
import org.junit.Test

import scala.collection.mutable.ArrayBuffer

class SbtOutputCompleteLinesProcessListenerTest {

  private val text =
    """ab
      |cd
      |ef
      |gh
      |""".stripMargin.withNormalizedSeparator

  private val expectedLines = Seq("ab", "cd", "ef", "gh")

  private val textWithEmptyLines =
    """ab
      |
      |cd
      |
      |""".stripMargin.withNormalizedSeparator

  private val expectedLinesWithEmptyLines = Seq("ab", "", "cd", "")

  @Test
  def splitTextToLines_EmptyLines(): Unit = {
    doSplitTextToLinesTest("\n\n\n", Seq("", "", ""))
  }

  @Test
  def splitTextToLines(): Unit = {
    doSplitTextToLinesTest(text, expectedLines)
  }

  @Test
  def splitTextToLines_WindowsLineSeparator(): Unit = {
    doSplitTextToLinesTest(text.replace("\n", "\r\n"), expectedLines)
  }

  @Test
  def splitTextToLines_WithBlankLines(): Unit = {
    doSplitTextToLinesTest(textWithEmptyLines, expectedLinesWithEmptyLines)
  }

  @Test
  def splitTextToLines_WithBlankLines_WindowsLineSeparator(): Unit = {
    doSplitTextToLinesTest(textWithEmptyLines.replace("\n", "\r\n"), expectedLinesWithEmptyLines)
  }

  private def doSplitTextToLinesTest(text: String, expectedLines: Seq[String]): Unit = {
    val splits = allPossibleStringSplits(text)

    for {(chunks, splitIdx) <- splits.zipWithIndex} {
      val listener = new CollectingLineListener

      for {chunk <- chunks} {
        listener.processCompleteLines(chunk)
      }

      val actualLines = listener.result
      assertEquals(
        s"Wrong lines for text split $splitIdx",
        expectedLines,
        actualLines
      )
    }
  }

  private class CollectingLineListener extends SbtOutputCompleteLinesProcessListener(OldShellModeProvider) {

    private val buffer = new ArrayBuffer[String]

    def result: Seq[String] = buffer.toSeq

    override def onLine(line: String): Unit = {
      buffer += line
    }
  }

  private object OldShellModeProvider extends SbtShellModeProvider {
    override def isNewShell: Boolean = false
  }

  @Test
  def allPossibleStringSplits(): Unit = {
    assertEquals(
      List(
        List("abcd"),
        List("a", "bcd"),
        List("ab", "cd"),
        List("abc", "d"),
        List("a", "b", "cd"),
        List("a", "bc", "d"),
        List("ab", "c", "d"),
        List("a", "b", "c", "d"),
      ),
      allPossibleStringSplits("""abcd""").sortBy(_.length)
    )
  }

  private def allPossibleStringSplits(input: String): List[List[String]] = {
    if (input.isEmpty) List(List())
    else (1 to input.length).flatMap { i =>
      val start = input.drop(i)
      val end = input.take(i)
      allPossibleStringSplits(start).map {
        end :: _
      }
    }.toList
  }
}
