package org.jetbrains.sbt.shell.communication

import com.intellij.execution.process.{AnsiEscapeDecoder, ProcessEvent, ProcessListener, ProcessOutputType, ProcessOutputTypes}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.sbt.shell.communication.SbtOutputCompleteLinesProcessListener.{LineSeparatorRegex, escapeNewLines, streamKey}

import scala.collection.mutable

/**
 * Converts process output chunks into line-oriented sbt shell events.
 *
 * Text delivered by [[onTextAvailable]] or [[coloredTextAvailable]] can split one console line across several callbacks.
 * Stdout and stderr are read on separate threads, so unfinished tails are tracked separately per base output stream.
 *
 * Shell communication is a text protocol layered over raw process streams. Most output can be forwarded as complete
 * lines, but shell prompts may be meaningful before a line separator arrives, and debugger/JDWP diagnostics may be
 * interleaved with those prompts. This listener therefore normalizes the stream into logical shell communication lines
 * before command listeners decide whether a command is complete.
 *
 * This listener:
 *  1. keeps the unfinished tail of the previous callback
 *  1. joins it with the next chunk,
 *  1. removes standalone debugger control lines that are not sbt command output,
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
    processCompleteLines(event.getText, outputType)

  final override def coloredTextAvailable(text: String, attributes: Key[?]): Unit =
    processCompleteLines(text, attributes)

  /**
   * Tracks the content of the last line until a new line character is processed.<br>
   * The key is the base stream, for example stdout or stderr.
   *
   * Stdout and stderr chunks can arrive on different reader threads. Keeping a single unfinished tail would allow
   * stderr diagnostics such as a JDWP banner to be prepended to a stdout prompt, which changes prompt recognition.
   */
  private val lastIncompleteLineByStream = mutable.Map.empty[Key[?], String]

  @TestOnly
  @Internal
  final def processCompleteLines(text: String): Unit =
    processCompleteLines(text, ProcessOutputTypes.STDOUT)

  @TestOnly
  @Internal
  final def processCompleteLines(text: String, outputType: Key[?]): Unit = {
    val lines = getCompleteLines(text, outputType)
    lines.foreach(onLine)
  }

  /**
   * Splits the next output chunk into complete logical lines.
   *
   * A complete physical line is usually emitted only after a line separator. There are two important exceptions:
   * sbt ready/error prompts are emitted immediately because they unblock shell communication, and JDWP listening banners
   * are filtered if they are exact debugger control output.
   *
   * Examples:
   * {{{
   * processCompleteLines("ab\ncd\n") emits Seq("ab", "cd")
   *
   * processCompleteLines("ab")
   * processCompleteLines("cd\n") emits Seq("abcd")
   *
   * processCompleteLines("[IJ]>") emits Seq("[IJ]>")
   *
   * processCompleteLines("[IJ]>Listening for transport dt_socket at address: 12345") emits Seq("[IJ]>")
   * }}}
   *
   * @param text a process output chunk with arbitrary boundaries; it may start
   *             or end with a line separator, contain several lines, or contain
   *             only part of a line. Supported line separators are `\n` and
   *             `\r\n`.
   * @return complete lines assembled after prepending the previously buffered incomplete tail.
   *         If the last assembled line is still incomplete,
   *         it is retained for the next call unless it is an sbt ready/error prompt.
   */
  private def getCompleteLines(text: String, outputType: Key[?]): Seq[String] = lastIncompleteLineByStream.synchronized {
    if (log.isTraceEnabled) {
      val textWithEscapedNewLines = escapeNewLines(text)
      log.trace(f"buildLine: $textWithEscapedNewLines")
    }

    val endsWithLineSeparator = text.endsWith("\n") || text.endsWith("\r\n")

    // Use a normalized stream key, so colored stdout/stderr output uses the same buffer as its base stream.
    val currentStreamKey = streamKey(outputType)
    val lastIncompleteLine = lastIncompleteLineByStream.getOrElse(currentStreamKey, "")
    val textWithRemainingLineContent = lastIncompleteLine + text

    // The "-1" argument keeps empty lines, including the trailing empty item when the chunk ends with a line separator.
    val lines = LineSeparatorRegex.pattern.split(textWithRemainingLineContent, -1).toSeq

    lastIncompleteLineByStream -= currentStreamKey

    if (endsWithLineSeparator) {
      // Flush all physical lines, but drop the trailing empty item produced by split(..., -1).
      lines.init.flatMap(communicationLines)
    } else {
      val lastLineOption = lines.lastOption
      val shouldFlushLastLine = lastLineOption.exists(isIncompleteLineReadyForCommunication)

      if (shouldFlushLastLine) {
        // Ready/error prompts can be written without a trailing line separator.
        // Emit them immediately so command execution can continue.
        lines.flatMap(communicationLines)
      } else {
        // Keep only the still-incomplete tail for this stream; complete preceding lines are emitted below.
        lastIncompleteLineByStream(currentStreamKey) = lastLineOption.getOrElse("")
        lines.init.flatMap(communicationLines)
      }
    }
  }

  private def isIncompleteLineReadyForCommunication(line: String): Boolean =
    SbtShellCommunicationLineNormalizer.isIncompleteLineReadyForCommunication(line, isNewSbtShell)

  private def communicationLines(line: String): Seq[String] =
    SbtShellCommunicationLineNormalizer.communicationLines(line, isNewSbtShell)
}

object SbtOutputCompleteLinesProcessListener {
  private val LineSeparatorRegex = """\r?\n""".r

  private def escapeNewLines(text: String): String =
    text
      .replace("\\n", "\\\\n").replace("\n", "\\n")
      .replace("\\r", "\\\\r").replace("\r", "\\r")

  /**
   * Returns the base process output stream used for buffering incomplete lines.
   *
   * Examples:
   * {{{
   * stdout with ANSI color attributes -> stdout
   * stderr with ANSI color attributes -> stderr
   * system output                     -> system output
   * }}}
   *
   * Colored process output types are distinct keys.<br>
   * Buffering by those exact keys would allow two fragments from the same stdout line to be split into different buffers just because their ANSI attributes differ.
   */
  private def streamKey(outputType: Key[?]): Key[?] =
    outputType match {
      case outputType: ProcessOutputType => outputType.getBaseOutputType
      case _ => outputType
    }
}
