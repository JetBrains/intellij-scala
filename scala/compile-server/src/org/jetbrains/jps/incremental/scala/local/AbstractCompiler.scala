package org.jetbrains.jps.incremental.scala
package local

import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.plugins.scala.compiler.diagnostics
import xsbti._
import xsbti.compile._

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Supplier
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

abstract class AbstractCompiler extends Compiler {

  def getReporter(client: Client): Reporter = new ClientReporter(client)

  def getLogger(client: Client): Logger = new ClientLogger(client) with JavacOutputParsing

  def getProgress(client: Client, sourcesCount: Int): ClientProgress = new ClientProgress(client, sourcesCount)

  private class ClientLogger(val client: Client) extends Logger {
    override def error(msg: Supplier[String]): Unit = {
      val txt = msg.get()
      client.error(txt)
    }

    override def warn(msg: Supplier[String]): Unit = {
      val txt = msg.get()
      client.warning(txt)
    }

    override def info(msg: Supplier[String]): Unit = {
      val txt = msg.get()
      client.info(txt)
    }

    override def debug(msg: Supplier[String]): Unit = {
      val txt = msg.get()
      client.internalInfo(txt)
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

      //noinspection ScalaExtractStringToBundle
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

    override def comment(position: Position, msg: String): Unit = logInClient(msg, position, MessageKind.Progress, Nil)

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
      logInClient(resultMsg, pos, kind, createDiagnostics(problem))
    }

    private def createDiagnostics(problem: Problem): List[diagnostics.Action] =
      problem.actions()
        .asScala
        .flatMap { action =>
          val workspaceEdit = action.edit()
          val textEdits = workspaceEdit.changes().asScala.flatMap { te =>
            val position = te.position()

            val startEnd = (for {
              start <- createStart(position)
              end <- createEnd(position)
            } yield (start, end)).orElse {
              createPointer(position).map(p => (p, p))
            }

            startEnd.map { case (start, end) =>
              val text = ansiColorCodePattern.replaceAllIn(te.newText(), "")
              diagnostics.TextEdit(start, end, text)
            }
          }

          if (textEdits.nonEmpty) {
            val title = ansiColorCodePattern.replaceAllIn(action.title(), "")
            Some(diagnostics.Action(title, diagnostics.WorkspaceEdit(textEdits.toList)))
          } else None
        }.toList

    private def logInClient(msg: String, pos: Position, kind: MessageKind, actions: List[diagnostics.Action]): Unit = {
      val source = pos.sourceFile.toScala

      // xsbti.Position#line, xsbti.Position#startLine and xsbti.Position#endLine contain 1-based line information.
      // xsbti.Position#pointer, xsbti.Position#startColumn and xsbti.Position#endColumn contain 0-based column information.

      val pointer = createPointer(pos)
      val problemStart = createStart(pos)
      val problemEnd = createEnd(pos)

      //noinspection ReferencePassedToNls
      client.message(kind, msg, source, pointer, problemStart, problemEnd, actions)
    }
  }

  private def createPointer(position: Position): Option[PosInfo] = for {
    line <- position.line().toScala
    column <- position.pointer().toScala
  } yield PosInfo(line, column + 1)

  private def createStart(position: Position): Option[PosInfo] = for {
    line <- position.startLine().toScala
    column <- position.startColumn().toScala
  } yield PosInfo(line, column + 1)

  private def createEnd(position: Position): Option[PosInfo] = for {
    line <- position.endLine().toScala
    column <- position.endColumn().toScala
  } yield PosInfo(line, column + 1)

  private val ansiColorCodePattern = "\\u001B\\[[\\d*]*m".r
}

