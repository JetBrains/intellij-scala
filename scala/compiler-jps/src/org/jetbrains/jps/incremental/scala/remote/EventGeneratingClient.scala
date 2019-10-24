package org.jetbrains.jps.incremental.scala
package remote

import java.io.File
import java.util.concurrent.TimeUnit

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import sbt.internal.inc.CompileFailed

/**
 * @author Pavel Fatin
 * @see [[org.jetbrains.jps.incremental.scala.ClientEventProcessor]]
 */
class EventGeneratingClient(writeEvent: Event => Unit, canceled: => Boolean) extends Client with AutoCloseable {

  private val eventGenerator = new AsynchEventGenerator(writeEvent)
  import eventGenerator.listener

  override def close(): Unit =
    eventGenerator.complete(20, TimeUnit.MINUTES)

  override def isCanceled: Boolean = canceled

  override def message(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]): Unit =
    listener(MessageEvent(kind, text, source, line, column))

  override def trace(exception: Throwable) {
    val message = exception match {
      case cf: CompileFailed => cf.toString // CompileFailed always has null message
      case _ => exception.getMessage
    }
    listener(TraceEvent(exception.getClass.getName, message, exception.getStackTrace))
  }

  override def progress(text: String, done: Option[Float]): Unit =
    listener(ProgressEvent(text, done))

  override def debug(text: String): Unit =
    listener(DebugEvent(text))

  override def generated(source: File, module: File, name: String): Unit =
    listener(GeneratedEvent(source, module, name))

  override def deleted(module: File): Unit =
    listener(DeletedEvent(module))

  override def processed(source: File): Unit =
    listener(SourceProcessedEvent(source))

  override def compilationEnd(): Unit =
    listener(CompilationEndEvent())

  override def processingEnd(): Unit =
    listener(ProcessingEndEvent())

  override def worksheetOutput(text: String): Unit =
    listener(WorksheetOutputEvent(text))

  override def sourceStarted(source: String): Unit =
    listener(CompilationStartedInSbt(source))
}
