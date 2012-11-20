package org.jetbrains.jps.incremental.scala

import xsbti._
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.MessageHandler

/**
 * @author Pavel Fatin
 */
class ProblemReporter(compilerName: String, messageHandler: MessageHandler) extends Reporter {
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

    val source = get(pos.sourcePath, "")
    val line = get(pos.line, java.lang.Integer.valueOf(-1)).toLong
    val column = get(pos.pointer, java.lang.Integer.valueOf(-1)).toLong

    messageHandler.processMessage(new CompilerMessage(compilerName, kind, msg, source, -1L, -1L, -1L, line, column))
  }

  def get[T](value: Maybe[T], default: T) = if (value.isDefined) value.get else default
}
