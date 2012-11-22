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
  def beginSource(source: File) {}

  def sourceDependency(dependsOn: File, source: File) {}

  def binaryDependency(binary: File, name: String, source: File) {}

  def generatedClass(source: File, module: File, name: String) {
    messageHandler.processMessage(new CompilerMessage(compilerName, Kind.PROGRESS, "Generated " + module.getPath))
    fileHandler.processFile(source, module)
  }

  def endSource(sourcePath: File) {}

  def api(sourceFile: File, source: SourceAPI) {}

  def problem(what: String, pos: Position, msg: String, severity: Severity, reported: Boolean) {}
}
