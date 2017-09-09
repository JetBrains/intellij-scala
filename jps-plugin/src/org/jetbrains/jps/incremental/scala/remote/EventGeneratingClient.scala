package org.jetbrains.jps.incremental.scala
package remote

import java.io.{File, PrintWriter, StringWriter}

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind

/**
 * @author Pavel Fatin
 */
class EventGeneratingClient(writeEvent: Event => Unit, canceled: => Boolean) extends Client {

  private val eventGenerator = new AsynchEventGenerator(writeEvent)
  import eventGenerator.listener

  def message(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]) {
    listener(MessageEvent(kind, text, source, line, column))
  }

  def trace(exception: Throwable) {
    val lines = {
      val writer = new StringWriter()
      exception.printStackTrace(new PrintWriter(writer))
      writer.toString.split("\\n")
    }
    listener(TraceEvent(exception.getMessage, lines))
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

  def isCanceled: Boolean = canceled

  def processed(source: File) {
    listener(SourceProcessedEvent(source))
  }

  override def compilationEnd() {
    listener(CompilationEndEvent())
    eventGenerator.complete()
  }

  override def worksheetOutput(text: String) {
    listener(WorksheetOutputEvent(text))
  }

  override def sourceStarted(source: String): Unit = listener(CompilationStartedInSbt(source))
}
