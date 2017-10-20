package org.jetbrains.jps.incremental.scala

import java.io.File

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind

/**
 * User: Dmitry.Naydanov
 * Date: 12.02.15.
 */
class DummyClient extends Client {
  override def message(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]) {}

  override def deleted(module: File) {}

  override def progress(text: String, done: Option[Float]) {}

  override def isCanceled: Boolean = false

  override def debug(text: String) {}

  override def processed(source: File) {}

  override def trace(exception: Throwable) {}

  override def generated(source: File, module: File, name: String) {}
}
