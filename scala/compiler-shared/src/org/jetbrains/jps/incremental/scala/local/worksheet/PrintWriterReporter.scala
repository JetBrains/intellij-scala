package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.util.Base64

import org.jetbrains.jps.incremental.scala.Client

class PrintWriterReporter(printWriter: PrintWriter, client: Client) extends ILoopWrapperReporter {

  import PrintWriterReporter._

  override def report(severity: String,
                      line: Integer,
                      column: Integer,
                      lineContent: String,
                      message: String): Unit = {
    val reportLine = s"$IJReportPrefix$severity:$line:$column:${encode(lineContent)}:${encode(message)}\n"
    printWriter.print(reportLine)
    printWriter.flush()
  }

  override def internalDebug(message: String): Unit =
    client.debug(message)
}

object PrintWriterReporter {

  val IJReportPrefix = "###IJ_REPORT###"
  private val IJReportRegexp= "(\\w+):(\\d+):(\\d+):(.*?):(.*)"
    .r("severity", "line", "column", "lineContentEncoded", "messageEncoded")

  case class MessageLineParsed(severity: String,
                               line: Integer,
                               column: Integer,
                               lineContent: String,
                               message: String)

  def parse(messageLine: String): Option[MessageLineParsed] = {
    messageLine match {
      case IJReportRegexp(severity, lineStr, columnStr, lineContentEncoded, messageEncoded) =>
        try {
          val line = lineStr.toInt
          val column = columnStr.toInt
          val message = decode(messageEncoded)
          val lineContent = decode(lineContentEncoded)
          Some(MessageLineParsed(severity, line, column, lineContent, message))
        } catch {
          case _: NumberFormatException => None
        }
      case _=> None
    }
  }

  private def encode(text: String): String = {
    val bytes = text.getBytes(StandardCharsets.UTF_8)
    Base64.getEncoder.encodeToString(bytes)
  }

  private def decode(text: String): String = {
    val bytes = Base64.getDecoder.decode(text)
    new String(bytes, StandardCharsets.UTF_8)
  }
}