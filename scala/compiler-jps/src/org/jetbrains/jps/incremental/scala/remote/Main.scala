package org.jetbrains.jps.incremental.scala.remote

import java.io._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.{Base64, Timer, TimerTask}

import com.martiansoftware.nailgun.NGContext
import org.jetbrains.jps.incremental.scala.data.ArgumentsParser
import org.jetbrains.jps.incremental.scala.local.LocalServer
import org.jetbrains.jps.incremental.scala.local.worksheet.WorksheetServer
import org.jetbrains.plugins.scala.compiler.data.Arguments

/**
 * Nailgun Nail, used in:
 *
 * @see [[org.jetbrains.plugins.scala.nailgun.NailgunRunner]]<br>
 *      [[org.jetbrains.plugins.scala.nailgun.NailgunMainLightRunner]]
 *      [[org.jetbrains.plugins.scala.compiler.NonServerRunner]]
 */
object Main {
  private val server = new LocalServer()
  private val worksheetServer = new WorksheetServer

  private var shutdownTimer: Timer = _

  /**
   * This method is called by NGServer
   *
   * @see [[http://www.martiansoftware.com/nailgun/quickstart.html]]<br>
   *      [[http://www.martiansoftware.com/nailgun/doc/javadoc/com/martiansoftware/nailgun/NGContext.html]]<br>
   *      [[com.martiansoftware.nailgun.NGContext]]<br>
   *      [[com.martiansoftware.nailgun.NGSession:153]]<br>
   *      [[com.martiansoftware.nailgun.NGServer:198]]<br>
   */
  def nailMain(context: NGContext): Unit = {
    cancelShutdownTimer()
    make(context.getArgs.toSeq, context.out, context.getNGServer.getPort, standalone = false)
    resetShutdownTimer(context)
  }

  def main(args: Array[String]): Unit = {
    make(args, System.out, -1, standalone = true)
  }

  private def make(argsEncoded: Seq[String], out: PrintStream, port: Int, standalone: Boolean): Unit = {
    val client = new EncodingEventGeneratingClient(out, standalone)
    val oldOut = System.out
    // Suppress any stdout data, interpret such data as error
    System.setOut(System.err)

    try {
      val args: Arguments = decodeArguments(argsEncoded) match {
        case Right(a) => a
        case Left(error) =>
          client.trace(error)
          return
      }


      // Don't check token in non-server mode
      if (port != -1) {
        try {
          compareTokenWith(tokenPathFor(port), args.token)
        } catch {
          // We must abort the process on _any_ error
          case e: Throwable =>
            client.error(e.getMessage)
            return
        }
      }

      val worksheetArgs = args.worksheetArgs
      if (!worksheetArgs.exists(_.isRepl)) {
        server.compile(args.sbtData, args.compilerData, args.compilationData, client)
      }

      worksheetArgs match {
        case Some(wa) if !client.hasErrors=>
          worksheetServer.loadAndRun(wa, args, client)
        case _ =>
      }
    } catch {
      case e: Throwable =>
        client.trace(e)
    } finally {
      client.processingEnd()
      client.close()
      System.setOut(oldOut)
    }
  }

  private def decodeArguments(argsEncoded: Seq[String]): Either[ArgumentsParser.ArgumentsParserError, Arguments] = {
    val args = argsEncoded.map(decodeArgument)
    ArgumentsParser.parse(args)
  }

  private def decodeArgument(argEncoded: String): String = {
    val decoded = Base64.getDecoder.decode(argEncoded.getBytes)
    val str = new String(decoded, StandardCharsets.UTF_8)
    if (str == "#STUB#") "" else str
  }

  private def tokenPathFor(port: Int): Path =
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

  private def cancelShutdownTimer(): Unit = synchronized {
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
        cancelShutdownTimer()
        shutdownTimer = new Timer()
        shutdownTimer.schedule(shutdownTask, delayMs)
      }
    }
  }
}
