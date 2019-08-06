package org.jetbrains.jps.incremental.scala
package remote

import java.io.File
import java.util.concurrent.TimeUnit

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import sbt.internal.inc.CompileFailed

/**
 * @author Pavel Fatin
 */
class EventGeneratingClient(writeEvent: Event => Unit, canceled: => Boolean) extends Client {

  private val eventGenerator = new AsynchEventGenerator(writeEvent)
  import eventGenerator.listener

  def isCanceled: Boolean = canceled

  def close() {
    eventGenerator.complete(20, TimeUnit.MINUTES)
  }

  def message(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]) {
    listener(MessageEvent(kind, text, source, line, column))
  }

  def trace(exception: Throwable) {
    val message = exception match {
      case cf: CompileFailed => cf.toString // CompileFailed always has null message
      case _ => exception.getMessage
    }
    listener(TraceEvent(exception.getClass.getName, message, exception.getStackTrace))
  }

  def progress(text: String, done: Option[Float]) {
    listener(ProgressEvent(text, done))
  }

  def debug(text: String) {
    listener(DebugEvent(text))
  }

  def generated(source: File, module: File, name: String) {
    listener(GeneratedEvent(source, module, name))
  }

  def deleted(module: File) {
    listener(DeletedEvent(module))
  }

  def processed(source: File) {
    listener(SourceProcessedEvent(source))
  }

  override def compilationEnd() {
    listener(CompilationEndEvent())
  }

  override def worksheetOutput(text: String) {
    listener(WorksheetOutputEvent(text))
  }

  override def sourceStarted(source: String) {
    listener(CompilationStartedInSbt(source))
  }
}
