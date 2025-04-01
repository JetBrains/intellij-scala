package org.jetbrains.sbt.shell

import com.fasterxml.jackson.core.JsonParseException
import org.jetbrains.sbt.shell.ServerUtil.stripContentLengthSuffix

import java.io.BufferedReader
import scala.annotation.tailrec
import scala.util.Try


class Listener(in: BufferedReader) extends Thread {

  override def run(): Unit = {
    @tailrec
    def processMessages(): Unit = {
      if (SbtServerClient.done) return
      def readMessageLine(): Option[String] = {
        val line1 = in.readLine()
        val line2 = in.readLine()
        val contentLength = {
          val ContentLengthPattern = """Content-Length:\s*(\d+)""".r
          line1 match {
            case ContentLengthPattern(length) => length.toInt
            case _ => line2 match {
              case ContentLengthPattern(length) => length.toInt
              case _ => 0
            }
          }
        }
        val buffer = new Array[Char](contentLength + 2)
        in.read(buffer, 0, contentLength + 2)
        Some(buffer.mkString.trim)
      }

      def handleMessage(messageLine: String): Unit = {
        val cleanedMessage = stripContentLengthSuffix(messageLine)
        if (isValidJson(cleanedMessage)) {
          try {
            SbtServerClient.queue.put(cleanedMessage)
          } catch {
            case _: Exception => ()
          }
        }
      }

      readMessageLine().foreach { messageLine =>
        Try(handleMessage(messageLine)).recover {
          case _ => () // Silently ignore exceptions
        }
      }

      processMessages()
    }

    processMessages()
  }


  private def isValidJson(str: String): Boolean = {
    import play.api.libs.json._
    try {
      Json.parse(str)
      true
    } catch {
      case _: JsonParseException | _: IllegalArgumentException => false
    }
  }

}