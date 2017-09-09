package org.jetbrains.jps.incremental.scala
package local

import java.io.File
import java.util.function.Supplier

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import xsbti._
import xsbti.compile._
import org.jetbrains.jps.incremental.scala.local.zinc.Utils._

import scala.collection.mutable

/**
 * Nikolay.Tropin
 * 11/18/13
 */
abstract class AbstractCompiler extends Compiler {


  def getReporter(client: Client): Reporter = new ClientReporter(client)

  def getLogger(client: Client, zincLogFilter: ZincLogFilter): Logger = new ClientLogger(client, zincLogFilter) with JavacOutputParsing

  def getProgress(client: Client): ClientProgress = new ClientProgress(client)

  private class ClientLogger(val client: Client, logFilter: ZincLogFilter) extends Logger {
    def error(msg: Supplier[String]) {
      val txt = msg.get()
      if (logFilter.shouldLog(Kind.ERROR, txt)) client.error(txt)
    }

    def warn(msg: Supplier[String]) {
      val txt = msg.get()
      if (logFilter.shouldLog(Kind.WARNING, txt)) client.warning(txt)
    }

    def info(msg: Supplier[String]) {
      val txt = msg.get()
      if (logFilter.shouldLog(Kind.INFO, txt)) client.info(txt)
    }

    def debug(msg: Supplier[String]) {
      val txt = msg.get()
      if (logFilter.shouldLog(Kind.PROGRESS, txt)) client.debug(txt)
    }

    def trace(exception: Supplier[Throwable]) {
      client.trace(exception.get())
    }
  }

  class ClientProgress(client: Client) extends CompileProgress {


    def startUnit(phase: String, unitPath: String) {
      val unitName = new File(unitPath).getName
      client.progress("Phase " + phase + " on " + unitName)
    }

    def advance(current: Int, total: Int): Boolean = {
      client.progress("", Some(current.toFloat / total.toFloat))
      !client.isCanceled
    }

    def generated(source: File, module: File, name: String): Unit = {
      client.generated(source, module, name)
    }

    def deleted(module: File) {
      client.deleted(module)
    }
  }

  private class ClientReporter(client: Client) extends Reporter {
    private val entries: mutable.Buffer[Problem] = mutable.Buffer.empty
    private var errorSeen = false
    private var warningSeen = false

    def reset() {
      entries.clear()
      errorSeen = false
      warningSeen = false
    }

    override def hasErrors(): Boolean = errorSeen

    override def hasWarnings(): Boolean = warningSeen

    override def printSummary(): Unit = {} // Not needed in Intellij-zinc integration

    override def problems: Array[Problem] = entries.reverse.toArray

    override def comment(position: Position, msg: String): Unit = logInClient(msg, position, Kind.PROGRESS)

    override def log(problem: Problem): Unit = {
      entries += problem

      val kind = problem.severity() match {
        case Severity.Info =>
          Kind.INFO
        case Severity.Warn =>
          warningSeen = true
          Kind.WARNING
        case Severity.Error =>
          errorSeen = true
          Kind.ERROR
      }

      val messageWithLineAndPointer = {
        val pos = problem.position()
        val indent = pos.pointerSpace.toOption.map("\n" + _ + "^").getOrElse("")
        s"${problem.message()}\n${pos.lineContent}\n$indent"
      }
      logInClient(messageWithLineAndPointer, problem.position(), kind)
    }

    private def logInClient(msg: String, pos: Position, kind: Kind): Unit = {
      val source = pos.sourceFile.toOption
      val line = pos.line.toOption.map(_.toLong)
      val column = pos.pointer.toOption.map(_.toLong + 1L)
      client.message(kind, msg, source, line, column)
    }
  }
}

