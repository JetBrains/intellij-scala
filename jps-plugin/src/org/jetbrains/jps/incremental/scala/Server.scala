package org.jetbrains.jps.incremental.scala

import data.{CompilationData, CompilerData, SbtData}
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import java.io.File
import org.jetbrains.jps.incremental.scala.model.IncrementalType

/**
 * @author Pavel Fatin
 */
trait Server {
  def compile(sbtData: SbtData, compilerData: CompilerData, compilationData: CompilationData, incrType: IncrementalType, client: Client): ExitCode
}

trait Client {
  def message(kind: Kind, text: String, source: Option[File] = None, line: Option[Long] = None, column: Option[Long] = None)

  def error(text: String, source: Option[File] = None, line: Option[Long] = None, column: Option[Long] = None) {
    message(Kind.ERROR, text, source, line, column)
  }

  def warning(text: String, source: Option[File] = None, line: Option[Long] = None, column: Option[Long] = None) {
    message(Kind.WARNING, text, source, line, column)
  }

  def info(text: String, source: Option[File] = None, line: Option[Long] = None, column: Option[Long] = None) {
    message(Kind.INFO, text, source, line, column)
  }

  def trace(exception: Throwable)

  def progress(text: String, done: Option[Float] = None)

  def debug(text: String)

  def generated(source: File, module: File, name: String)

  def deleted(module: File)

  def isCanceled: Boolean
}
