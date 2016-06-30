package org.jetbrains.jps.incremental.scala

import java.io.File

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind

/**
 * @author Pavel Fatin
 */
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

  def processed(source: File)

  def deleted(module: File)

  def isCanceled: Boolean
  
  def worksheetOutput(text: String) {}
  
  def compilationEnd() {}
}

