package org.jetbrains.jps.incremental.scala
package local

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Supplier

import org.jetbrains.jps.incremental.scala.Client.PosInfo
import xsbti._
import xsbti.compile._

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

abstract class AbstractCompiler extends Compiler {

  def getReporter(client: Client): Reporter = new ClientReporter(client)

  def getLogger(client: Client, zincLogFilter: ZincLogFilter): Logger = new ClientLogger(client, zincLogFilter) with JavacOutputParsing

  def getProgress(client: Client, sourcesCount: Int): ClientProgress = new ClientProgress(client, sourcesCount)

  private class ClientLogger(val client: Client, logFilter: ZincLogFilter) extends Logger {
    override def error(msg: Supplier[String]): Unit = {
      val txt = msg.get()
      if (logFilter.shouldLog(MessageKind.Error, txt)) client.error(txt)
    }

    override def warn(msg: Supplier[String]): Unit = {
      val txt = msg.get()
      if (logFilter.shouldLog(MessageKind.Warning, txt)) client.warning(txt)
    }

    override def info(msg: Supplier[String]): Unit = {
      val txt = msg.get()
      if (logFilter.shouldLog(MessageKind.Info, txt)) client.info(txt)
    }

    override def debug(msg: Supplier[String]): Unit = {
      val txt = msg.get()
      if (logFilter.shouldLog(MessageKind.Progress, txt)) client.internalInfo(txt)
    }

    override def trace(exception: Supplier[Throwable]): Unit = {
      client.trace(exception.get())
    }
  }

  class ClientProgress(client: Client, sourcesCount: Int)
    extends CompileProgress {

    private var cancelLastTimeChecked = System.currentTimeMillis()
    private final val cancelThreshold = 1000L

    private var currentPhase: String = _

    override def startUnit(phase: String, unitPath: String): Unit = {
      if (phase != currentPhase) {
        client.compilationPhase(phase)
        currentPhase = phase
      }
      client.compilationUnit(unitPath)
    }

    override def advance(current: Int, total: Int, prevPhase: String, nextPhase: String): Boolean = {
      val currentTime = System.currentTimeMillis()

      client.progress("", Some(current.toFloat / total.toFloat))

      // Since isCanceled is blocking method (waiting on flush on socket connection to finish).
      // We don't want to block compilation more often then once per second (this is optimization)
      // It also means that compilation may be canceled 1 sec later - but this is not a problem.
      if (currentTime - cancelLastTimeChecked > cancelThreshold) {
        cancelLastTimeChecked = currentTime
        !client.isCanceled
      } else true
    }
  }

  private class ClientReporter(client: Client) extends Reporter {
    private val entries: ConcurrentLinkedQueue[Problem] = new ConcurrentLinkedQueue()
    private var errorSeen = false
    private var warningSeen = false

    override def reset(): Unit = {
      entries.clear()
      errorSeen = false
      warningSeen = false
    }

    override def hasErrors: Boolean = errorSeen

    override def hasWarnings: Boolean = warningSeen

    override def printSummary(): Unit = {} // Not needed in Intellij-zinc integration

    override def problems: Array[Problem] = entries.asScala.toArray.reverse

    override def comment(position: Position, msg: String): Unit = logInClient(msg, position, MessageKind.Progress)

    override def log(problem: Problem): Unit = {
      entries.add(problem)

      val kind = problem.severity() match {
        case Severity.Info =>
          MessageKind.Info
        case Severity.Warn =>
          warningSeen = true
          MessageKind.Warning
        case Severity.Error =>
          errorSeen = true
          MessageKind.Error
      }

      val pos = problem.position
      val messageWithoutAnsiColorCodes = ansiColorCodePattern.replaceAllIn(problem.message(), "")
      val resultMsg = s"${messageWithoutAnsiColorCodes}\n${pos.lineContent}\n"
      logInClient(resultMsg, pos, kind)
    }

    private def logInClient(msg: String, pos: Position, kind: MessageKind): Unit = {
      val source = pos.sourceFile.toScala

      // xsbti.Position#line, xsbti.Position#startLine and xsbti.Position#endLine contain 1-based line information.
      // xsbti.Position#pointer, xsbti.Position#startColumn and xsbti.Position#endColumn contain 0-based column information.

      val pointer = for {
        line <- pos.line().toScala
        column <- pos.pointer().toScala
      } yield PosInfo(line, column + 1)

      val problemStart = for {
        line <- pos.startLine().toScala
        column <- pos.startColumn().toScala
      } yield PosInfo(line, column + 1)

      val problemEnd = for {
        line <- pos.endLine().toScala
        column <- pos.endColumn().toScala
      } yield PosInfo(line, column + 1)

      //noinspection ReferencePassedToNls
      client.message(kind, msg, source, pointer, problemStart, problemEnd)
    }
  }

  private val ansiColorCodePattern = "\\u001B\\[[\\d*]*m".r
}

