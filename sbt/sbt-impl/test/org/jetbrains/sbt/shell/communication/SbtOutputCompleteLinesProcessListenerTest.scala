package org.jetbrains.sbt.shell.communication

import com.intellij.execution.process.ProcessOutputTypes
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

  private val JdwpListeningMessages = Seq(
    "Listening for transport dt_socket at address: 12345",
    "Listening for transport dt_shmem at address: javadebug",
    "Listening for transport dt_shmem at address: javadebug.2",
    "Listening for transport dt_shmem at address: customDebugMemory",
  )

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

  @Test
  def splitTextToLines_OldShellPrompt_GluedToJdwpListeningMessage(): Unit =
    doPromptGluedToJdwpListeningMessageTest(
      "[IJ]>",
      Seq("[IJ]>"),
      OldShellModeProvider,
    )

  @Test
  def splitTextToLines_NewShellPrompt_GluedToJdwpListeningMessage(): Unit =
    doPromptGluedToJdwpListeningMessageTest(
      "sbt:mock>",
      Seq("sbt:mock>"),
      NewShellModeProvider,
    )

  @Test
  def splitTextToLines_StandaloneJdwpListeningMessagesAreIgnored(): Unit =
    for (message <- JdwpListeningMessages) {
      doSelectedSplitTextToLinesTest(
        message + "\n",
        Seq.empty,
        OldShellModeProvider,
      )
    }

  @Test
  def splitTextToLines_ProjectLoadingFailurePromptWithoutLineSeparator(): Unit = {
    val prompt = "Project loading failed: (r)etry, (q)uit, (l)ast, or (i)gnore?"

    doSelectedSplitTextToLinesTest(prompt, Seq(prompt), OldShellModeProvider)
    doSelectedSplitTextToLinesTest(prompt, Seq(prompt), NewShellModeProvider)
  }

  @Test
  def splitTextToLines_JdwpListeningMessageInsideRegularOutputIsPreserved(): Unit =
    for (message <- JdwpListeningMessages) {
      doSelectedSplitTextToLinesTest(
        s"regular output: $message\n",
        Seq(s"regular output: $message"),
        OldShellModeProvider,
      )
    }

  @Test
  def splitTextToLines_OldShellPrompt_IsNotBlockedByBufferedStderrJdwpListeningMessage(): Unit = {
    val listener = new CollectingLineListener(OldShellModeProvider)

    listener.processCompleteLines("Listening for transport dt_socket at address: 12345", ProcessOutputTypes.STDERR)
    listener.processCompleteLines("[IJ]>", ProcessOutputTypes.STDOUT)
    listener.processCompleteLines("\n", ProcessOutputTypes.STDERR)

    assertEquals(Seq("[IJ]>"), listener.linesResult)
  }

  @Test
  def splitTextToLines_NewShellPrompt_IsNotBlockedByBufferedStderrJdwpListeningMessage(): Unit = {
    val listener = new CollectingLineListener(NewShellModeProvider)

    listener.processCompleteLines("Listening for transport dt_socket at address: 12345", ProcessOutputTypes.STDERR)
    listener.processCompleteLines("sbt:mock>", ProcessOutputTypes.STDOUT)
    listener.processCompleteLines("\n", ProcessOutputTypes.STDERR)

    assertEquals(Seq("sbt:mock>"), listener.linesResult)
  }

  private def doPromptGluedToJdwpListeningMessageTest(
    prompt: String,
    expectedLines: Seq[String],
    shellModeProvider: SbtShellModeProvider,
  ): Unit = {
    for ((jdwpListeningBanner, bannerIdx) <- JdwpListeningMessages.zipWithIndex) {
      val text = prompt + jdwpListeningBanner
      val splits = Seq(
        Seq(text),
        Seq(prompt, jdwpListeningBanner),
        Seq(prompt + "Listening", jdwpListeningBanner.stripPrefix("Listening")),
        Seq(prompt + "Listening for transport", jdwpListeningBanner.stripPrefix("Listening for transport")),
      )

      for {(chunks, splitIdx) <- splits.zipWithIndex} {
        val listener = new CollectingLineListener(shellModeProvider)

        for {chunk <- chunks} {
          listener.processCompleteLines(chunk)
        }

        val actualLines = listener.linesResult
        assertEquals(
          s"Wrong lines for prompt/JDWP banner $bannerIdx split $splitIdx",
          expectedLines,
          actualLines
        )
      }
    }
  }

  private def doSelectedSplitTextToLinesTest(
    text: String,
    expectedLines: Seq[String],
    shellModeProvider: SbtShellModeProvider,
  ): Unit = {
    val splits = selectedStringSplits(text)

    for {(chunks, splitIdx) <- splits.zipWithIndex} {
      val listener = new CollectingLineListener(shellModeProvider)

      for {chunk <- chunks} {
        listener.processCompleteLines(chunk)
      }

      val actualLines = listener.linesResult
      assertEquals(
        s"Wrong lines for selected text split $splitIdx",
        expectedLines,
        actualLines
      )
    }
  }

  private def doSplitTextToLinesTest(text: String, expectedLines: Seq[String]): Unit = {
    doSplitTextToLinesTest(text, expectedLines, OldShellModeProvider)
  }

  private def doSplitTextToLinesTest(
    text: String,
    expectedLines: Seq[String],
    shellModeProvider: SbtShellModeProvider,
  ): Unit = {
    val splits = allPossibleStringSplits(text)

    for {(chunks, splitIdx) <- splits.zipWithIndex} {
      val listener = new CollectingLineListener(shellModeProvider)

      for {chunk <- chunks} {
        listener.processCompleteLines(chunk)
      }

      val actualLines = listener.linesResult
      assertEquals(
        s"Wrong lines for text split $splitIdx",
        expectedLines,
        actualLines
      )
    }
  }

  private class CollectingLineListener(shellModeProvider: SbtShellModeProvider)
    extends SbtOutputCompleteLinesProcessListener(shellModeProvider) {

    private val lines = new ArrayBuffer[String]

    def linesResult: Seq[String] = lines.toSeq

    override def onLine(line: String): Unit = {
      lines += line
    }
  }

  private object OldShellModeProvider extends SbtShellModeProvider {
    override def isNewShell: Boolean = false
  }

  private object NewShellModeProvider extends SbtShellModeProvider {
    override def isNewShell: Boolean = true
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

  /**
   * Returns a focused set of chunk splits for longer strings where testing every possible split would be too expensive.
   *
   * For example, `selectedStringSplits("abc")` returns:
   * {{{
   * Seq(
   *   Seq("abc"),
   *   Seq("a", "b", "c"),
   *   Seq("a", "bc"),
   *   Seq("ab", "c"),
   * )
   * }}}
   */
  private def selectedStringSplits(input: String): Seq[Seq[String]] = {
    val parts1 = Seq(
      Seq(input),
      input.map(_.toString),
    )
    val parts2 = (1 until input.length).map { splitIndex =>
      Seq(input.take(splitIndex), input.drop(splitIndex))
    }
    parts1 ++ parts2
  }
}
