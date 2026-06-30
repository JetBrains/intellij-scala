package org.jetbrains.sbt.runner

import com.intellij.execution.process.{AnsiEscapeDecoder, ProcessHandler, ProcessOutputType}
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.build.BuildMessages
import org.jetbrains.sbt.shell.communication.{SbtShellBuildMessagesEventProcessor, SbtShellCommandEventProcessor, ShellEvent}

private[jetbrains] final class RunContentConsoleOutputProcessor(processHandler: ProcessHandler)
  extends SbtShellCommandEventProcessor.ListenerLike {

  private val ansiEscapeDecoder = new AnsiEscapeDecoder

  override def process(event: ShellEvent): Unit =
    event match {
      case ShellEvent.Output(text) =>
        val textWithLineSeparator = text + System.lineSeparator()
        // TODO: Preserve colored output in ShellEvent instead of decoding raw text at this final rendering step.
        //  SbtOutputCompleteLinesProcessListener currently emits plain ShellEvent.Output lines, so colors already decoded
        //  by the sbt shell process handler are lost. A complete solution would carry colored fragments/output keys through
        //  shell events and let consumers render colors or strip ANSI codes depending on their target UI.
        ansiEscapeDecoder.escapeText(textWithLineSeparator, outputType(text), (decodedText, attributes) =>
          processHandler.notifyTextAvailable(decodedText, attributes)
        )
      case _ =>
        ()
    }

  private def outputType(text: String): Key[?] = {
    val normalizedText = BuildMessages.stripAnsiCodes(text).trim
    if (SbtShellBuildMessagesEventProcessor.isErrorOutput(normalizedText))
      ProcessOutputType.STDERR
    else
      ProcessOutputType.STDOUT
  }
}
