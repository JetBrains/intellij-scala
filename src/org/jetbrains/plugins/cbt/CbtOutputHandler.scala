package org.jetbrains.plugins.cbt

import com.intellij.execution.process.AnsiEscapeDecoder.ColoredTextAcceptor
import com.intellij.execution.process.{AnsiEscapeDecoder, ProcessOutputTypes}
import com.intellij.openapi.util.Key

import scala.collection.mutable

class CbtOutputHandler(onOutput: (String, Boolean) => Unit) {

  private val ansiCodesEscaper = new AnsiCodesEscaper(onStdErr)

  private val warningsList = mutable.ListBuffer.empty[String]
  private val errorsList = mutable.ListBuffer.empty[String]
  private val outBuffer = StringBuilder.newBuilder
  private val errorBuffer = StringBuilder.newBuilder

  def dataReceived(text: String, stderr: Boolean): Unit = {
    if (stderr) {
      ansiCodesEscaper.escape(text)
    } else {
      outBuffer.append(text)
      onOutput(text, false)
    }
  }

  def stdOut: String =
    outBuffer.toString

  def stdErr: String =
    errorBuffer.toString

  def errors: Seq[String] =
    errorsList

  def warnngs: Seq[String] =
    warningsList

  private def onStdErr(text: String): Unit = {
    errorBuffer.append(text + "\n")
    onOutput(text + "\n", true)
    if (isError(text)) {
      errorsList += text
    } else if (isWarning(text)) {
      warningsList += text
    }
  }

  private def isError(line: String) =
    line.startsWith("[error]")

  private def isWarning(line: String) =
    line.startsWith("[warn]")

  private class AnsiCodesEscaper(onMessage: String => Unit) {
    private val colorEncoder = new AnsiEscapeDecoder
    private val messageBuffer = new StringBuilder
    private val textAcceptor = new ColoredTextAcceptor {
      override def coloredTextAvailable(text: String, attributes: Key[_]): Unit = {
        concatErrorMessages(text)
      }
    }

    def escape(text: String): Unit =
      colorEncoder.escapeText(text, ProcessOutputTypes.STDERR, textAcceptor)

    private def concatErrorMessages(text: String) =
      text.trim match {
        case "[" =>
          messageBuffer.append("[")
        case "]" =>
          messageBuffer.append("] ")
        case _ if messageBuffer.nonEmpty && messageBuffer.endsWith("[") =>
          messageBuffer.append(text.trim)
        case _ if messageBuffer.nonEmpty =>
          messageBuffer.append(text.rtrim)
          onMessage(messageBuffer.toString())
          messageBuffer.clear()
        case _ => onMessage(text.rtrim)
      }
  }

}
