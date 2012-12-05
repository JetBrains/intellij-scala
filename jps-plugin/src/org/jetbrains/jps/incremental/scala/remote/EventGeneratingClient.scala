package org.jetbrains.jps.incremental.scala
package remote

import java.io.File
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind

/**
 * @author Pavel Fatin
 */
class EventGeneratingClient(listener: Event => Unit) extends Client {
  def message(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]) {
    listener(MessageEvent(kind, text, source, line, column))
  }

  def trace(exception: Throwable) {
    listener(TraceEvent(exception))
  }

  def progress(text: String, done: Option[Float]) {
    listener(ProgressEvent(text, done))
  }

  def generated(source: File, module: File, name: String) {
    listener(GeneratedEvent(source, module, name))
  }

  def isCanceled = false
}
