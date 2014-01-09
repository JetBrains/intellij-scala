package org.jetbrains.jps.incremental.scala
package local

import xsbti._
import xsbti.compile.ExtendedCompileProgress
import java.io.File
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import scala.Some

/**
 * Nikolay.Tropin
 * 11/18/13
 */
abstract class AbstractCompiler extends Compiler {

  def getReporter(client: Client): Reporter = new ClientReporter(client)

  def getLogger(client: Client): Logger = new ClientLogger(client) with JavacOutputParsing

  def getProgress(client: Client): ExtendedCompileProgress = new ClientProgress(client)

  private class ClientLogger(val client: Client) extends Logger {
    def error(msg: F0[String]) {
      client.error(msg())
    }

    def warn(msg: F0[String]) {
      client.warning(msg())
    }

    def info(msg: F0[String]) {
      client.progress(msg())
    }

    def debug(msg: F0[String]) {
      val lines = msg().trim.split('\n').map(ClientLogger.trim)
      client.debug("\n\n" + lines.mkString("\n") + "\n")
    }

    def trace(exception: F0[Throwable]) {
      client.trace(exception())
    }
  }

  private object ClientLogger {
    private val MaxLineLength = 500

    private def trim(line: String) =
      if (line.length > MaxLineLength) line.substring(0, MaxLineLength) + "..." else line
  }

  private class ClientProgress(client: Client) extends ExtendedCompileProgress {
    def generated(source: File, module: File, name: String) {
      client.progress("Generated " + module.getName)
      client.generated(source, module, name)
    }

    def deleted(module: File) {
      client.deleted(module)
    }

    def startUnit(phase: String, unitPath: String) {
      val unitName = new File(unitPath).getName
      client.progress("Phase " + phase + " on " + unitName)
    }

    def advance(current: Int, total: Int) = {
      client.progress("", Some(current.toFloat / total.toFloat))
      !client.isCanceled
    }
  }

  private class ClientReporter(client: Client) extends Reporter {
    private var entries: List[Problem] = Nil

    def reset() {
      entries = Nil
    }

    def hasErrors = entries.exists(_.severity == Severity.Error)

    def hasWarnings = entries.exists(_.severity == Severity.Warn)

    def printSummary() {}

    def problems = entries.reverse.toArray

    def log(pos: Position, msg: String, sev: Severity) {
      entries ::= new Problem {
        val category = ""
        val position = pos
        val message = msg
        val severity = sev
      }

      val kind = sev match {
        case Severity.Info => Kind.INFO
        case Severity.Warn => Kind.WARNING
        case Severity.Error => Kind.ERROR
      }

      val source = toOption(pos.sourcePath).map(new File(_))
      val line = toOption(pos.line).map(_.toLong)
      val column = toOption(pos.pointer).map(_.toLong + 1L)

      val messageWithLineAndPointer = {
        val indent = toOption(pos.pointerSpace)
        msg + "\n" + pos.lineContent + indent.map("\n" + _ + "^").getOrElse("")
      }

      client.message(kind, messageWithLineAndPointer, source, line, column)
    }

    def toOption[T](value: Maybe[T]): Option[T] = if (value.isDefined) Some(value.get) else None

    def comment(p1: Position, p2: String) {} // TODO
  }

}

