package org.jetbrains.jps.incremental.scala

import org.jetbrains.jps.incremental.MessageHandler
import xsbti.{Maybe, Severity, Position, AnalysisCallback}
import java.io.File
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import xsbti.api.SourceAPI

/**
 * @author Pavel Fatin
 */
class Analyzer(compilerName: String, messageHandler: MessageHandler, fileHandler: FileHandler) extends AnalysisCallback {
  def beginSource(source: File) {
    messageHandler.processMessage(new CompilerMessage(compilerName, Kind.PROGRESS, "Reading " + source.getPath))
  }

  def sourceDependency(dependsOn: File, source: File) {}

  def binaryDependency(binary: File, name: String, source: File) {}

  def generatedClass(source: File, module: File, name: String) {
    messageHandler.processMessage(new CompilerMessage(compilerName, Kind.PROGRESS, "Generated " + module.getPath))
    fileHandler.processFile(source, module)
  }

  def endSource(sourcePath: File) {}

  def api(sourceFile: File, source: SourceAPI) {}

  def problem(what: String, pos: Position, msg: String, severity: Severity, reported: Boolean) {
    val kind = severity match {
      case Severity.Info => Kind.INFO
      case Severity.Warn => Kind.WARNING
      case Severity.Error => Kind.ERROR
    }

    val source = get(pos.sourcePath, "")
    val line = get(pos.line, java.lang.Integer.valueOf(-1)).toLong
    val column = get(pos.offset, java.lang.Integer.valueOf(-1)).toLong

    messageHandler.processMessage(new CompilerMessage(compilerName, kind, msg, source, -1L, -1L, -1L, line, column))
  }

  def get[T](value: Maybe[T], default: T) = if (value.isDefined) value.get else default
}
