package org.jetbrains.sbt.shell.communication

import com.intellij.execution.process.{AnsiEscapeDecoder, ProcessEvent, ProcessListener}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.sbt.shell.communication.SbtOutputCompleteLinesProcessListener.{LineSeparatorRegex, escapeNewLines}

/**
 * Converts process output chunks into line-oriented sbt shell events.
 *
 * Text delivered by [[onTextAvailable]] or [[coloredTextAvailable]] can split one console line across several callbacks.
 *
 * This listener:
 *  1. keeps the unfinished tail of the previous callback
 *  1. joins it with the next chunk,
 *  1. invokes [[onLine]] only for complete lines or for sbt prompts that are meaningful even without a trailing line separator
 */
private[shell] abstract class SbtOutputCompleteLinesProcessListener(
  shellModeProvider: SbtShellModeProvider
) extends ProcessListener with AnsiEscapeDecoder.ColoredTextAcceptor {

  def this(project: Project) =
    this(new SbtShellModeProviderImpl(project))

  protected val log: Logger = Logger.getInstance(getClass)

  protected final def isNewSbtShell: Boolean =
    shellModeProvider.isNewShell

  def onLine(line: String): Unit

  final override def onTextAvailable(event: ProcessEvent, outputType: Key[?]): Unit =
    processCompleteLines(event.getText)

  final override def coloredTextAvailable(text: String, attributes: Key[?]): Unit =
    processCompleteLines(text)

  /**
   * Tracks the content of the last line until a new line character is processed
   */
  private var lastIncompleteLine: String = ""

  @TestOnly
  @Internal
  final def processCompleteLines(text: String): Unit = {
    val lines = getCompleteLines(text)
    lines.foreach(onLine)
  }

  /**
   * Splits the next output chunk into complete logical lines.
   *
   * @param text a process output chunk with arbitrary boundaries; it may start
   *             or end with a line separator, contain several lines, or contain
   *             only part of a line. Supported line separators are `\n` and
   *             `\r\n`.
   * @return complete lines assembled after prepending the previously buffered incomplete tail.
   *         If the last assembled line is still incomplete,
   *         it is retained for the next call unless it is an sbt ready/error prompt.
   */
  private def getCompleteLines(text: String): Seq[String] = lastIncompleteLine.synchronized {
    if (log.isTraceEnabled) {
      val textWithEscapedNewLines = escapeNewLines(text)
      log.trace(f"buildLine: $textWithEscapedNewLines")
    }

    val endsWithLineSeparator = text.endsWith("\n") || text.endsWith("\r\n")

    val textWithRemainingLineContent = lastIncompleteLine + text

    //split lines by line separator, "-1" argument is to keep empty lines
    val lines = LineSeparatorRegex.pattern.split(textWithRemainingLineContent, -1).toSeq

    lastIncompleteLine = ""

    if (endsWithLineSeparator) {
      //flush all lines, but drop trailing empty line
      //(it's an empty string, because we used '-1' in 'split' method)
      lines.init
    } else {
      val lastLineOption = lines.lastOption
      val shouldFlushLastLine = lastLineOption.exists { line =>
        SbtProcessUtil.promptReady(line, isNewSbtShell) || SbtProcessUtil.promptError(line)
      }

      if (shouldFlushLastLine) {
        //NOTE: last line with IJ prompt or error might not have new line character in the end
        //But we still want it to be reported the line to detect that the console is "ready"
        lines
      } else {
        lastIncompleteLine = lastLineOption.getOrElse("")
        lines.init
      }
    }
  }
}

object SbtOutputCompleteLinesProcessListener {
  private val LineSeparatorRegex = """\r?\n""".r

  private def escapeNewLines(text: String): String =
    text
      .replace("\\n", "\\\\n").replace("\n", "\\n")
      .replace("\\r", "\\\\r").replace("\r", "\\r")
}
