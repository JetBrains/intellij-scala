package org.jetbrains.jps.incremental.scala
package remote

import java.io._
import java.nio.file.{Files, Path, Paths}
import java.util.{Timer, TimerTask}

import com.intellij.util.Base64Converter
import com.martiansoftware.nailgun.NGContext
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.local.LocalServer
import org.jetbrains.jps.incremental.scala.local.worksheet.WorksheetServer

/**
 * @author Pavel Fatin
 * @author Dmitry Naydanov
 */
object Main {
  private val Server = new LocalServer()
  private val worksheetServer = new WorksheetServer

  private var shutdownTimer: Timer = _

  def nailMain(context: NGContext): Unit = {
    cancelShutdown()
    make(context.getArgs.toSeq, context.out, context.getNGServer.getPort, standalone = false)
    resetShutdownTimer(context)
  }

  // Started by NonServerRunner
  def main(args: Array[String]) {
    make(args, System.out, -1, standalone = true)
  }

  private def make(arguments: Seq[String], out: PrintStream, port: Int, standalone: Boolean): Unit = {
    var hasErrors = false

    val client = {
      val eventHandler = (event: Event) => {
        val encode = Base64Converter.encode(event.toBytes)
        out.write((if (standalone && !encode.endsWith("=")) encode + "=" else encode).getBytes)
      }
      new EventGeneratingClient(eventHandler, out.checkError) {
        override def error(text: String, source: Option[File], line: Option[Long], column: Option[Long]): Unit = {
          hasErrors = true
          super.error(text, source, line, column)
        }

        override def message(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]): Unit = {
          if (kind == Kind.ERROR) hasErrors = true
          super.message(kind, text, source, line, column)
        }
      }
    }

    val oldOut = System.out
    // Suppress any stdout data, interpret such data as error
    System.setOut(System.err)

    try {
      val args = {
        val strings = arguments.map {
          arg =>
            val s = new String(Base64Converter.decode(arg.getBytes), "UTF-8")
            if (s == "#STUB#") "" else s
        }
        Arguments.from(strings)
      }

      // Don't check token in non-server mode
      if (port != -1) {
        try {
          compareTokenWith(tokenPathFor(port), args.token)
        } catch {
          // We must abort the process on _any_ error
          case e: Throwable =>
            client.error(e.getMessage)
            client.compilationEnd()
            return
        }
      }

      if (!worksheetServer.isRepl(args)) Server.compile(args.sbtData, args.compilerData, args.compilationData, client)

      if (!hasErrors) worksheetServer.loadAndRun(args, out, client, standalone)
    } catch {
      case e: Throwable =>
        client.trace(e)
    } finally {
      System.setOut(oldOut)
    }
  }

  private def tokenPathFor(port: Int) =
    Paths.get(System.getProperty("user.home"), ".idea-build", "tokens", port.toString)

  @throws(classOf[TokenVerificationException])
  private def compareTokenWith(path: Path, actualToken: String): Unit = {
    if (!path.toFile.exists) {
      throw new TokenVerificationException("Token not found: " + path)
    }

    val expectedToken = try {
      new String(Files.readAllBytes(path))
    } catch {
      case _: IOException =>
        throw new TokenVerificationException("Cannot read token: " + path)
    }

    if (!expectedToken.equals(actualToken)) {
      throw new TokenVerificationException("Token is incorrect:  " + actualToken)
    }
  }

  private class TokenVerificationException(message: String) extends Exception(message)

  private def cancelShutdown(): Unit = synchronized {
    if (shutdownTimer != null) {
      shutdownTimer.cancel()
      shutdownTimer = null
    }
  }

  private def resetShutdownTimer(context: NGContext): Unit = {
    val delay = Option(System.getProperty("shutdown.delay")).map(_.toInt)
    delay.foreach { t =>
      val delayMs = t * 60 * 1000
      val shutdownTask = new TimerTask {
        override def run(): Unit = context.getNGServer.shutdown(true)
      }

      synchronized {
        cancelShutdown()
        shutdownTimer = new Timer()
        shutdownTimer.schedule(shutdownTask, delayMs)
      }
    }
  }
}
