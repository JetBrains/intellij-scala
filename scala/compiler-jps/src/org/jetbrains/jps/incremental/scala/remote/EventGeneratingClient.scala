package org.jetbrains.jps.incremental.scala
package remote

import java.io.File
import java.util.concurrent.TimeUnit

import sbt.internal.inc.CompileFailed

/**
 * @author Pavel Fatin
 * @see [[org.jetbrains.jps.incremental.scala.ClientEventProcessor]]
 */
class EventGeneratingClient(writeEvent: Event => Unit, canceled: => Boolean) extends Client with AutoCloseable {

  private val eventGenerator = new AsynchEventGenerator(writeEvent)

  private def publishEvent(event: Event): Unit =
    eventGenerator.listener(event)

  override def close(): Unit =
    eventGenerator.complete(20, TimeUnit.MINUTES)

  override def isCanceled: Boolean = canceled

  override def message(msg: Client.ClientMsg): Unit = {
    val Client.ClientMsg(kind, text, source, from, to) = msg
    publishEvent(MessageEvent(kind, text, source, from, to))
  }

  override def trace(exception: Throwable): Unit = {
    val message = exception match {
      case cf: CompileFailed => cf.toString // CompileFailed always has null message
      case _ => exception.getMessage
    }
    publishEvent(TraceEvent(exception.getClass.getName, message, exception.getStackTrace))
  }

  override def progress(text: String, done: Option[Float]): Unit =
    publishEvent(ProgressEvent(text, done))

  override def internalInfo(text: String): Unit =
    publishEvent(InfoEvent(text))

  override def internalDebug(text: String): Unit =
    publishEvent(DebugEvent(text))

  override def generated(source: File, module: File, name: String): Unit =
    publishEvent(GeneratedEvent(source, module, name))

  override def deleted(module: File): Unit =
    publishEvent(DeletedEvent(module))

  override def compilationStart(): Unit =
    publishEvent(CompilationStartEvent())

  override def compilationEnd(sources: Set[File]): Unit =
    publishEvent(CompilationEndEvent(sources))

  override def processingEnd(): Unit =
    publishEvent(ProcessingEndEvent())

  override def worksheetOutput(text: String): Unit =
    publishEvent(WorksheetOutputEvent(text))

  override def sourceStarted(source: String): Unit =
    publishEvent(CompilationStartedInSbt(source))

  override def meteringInfo(info: CompileServerMeteringInfo): Unit =
    publishEvent(MeteringInfo(info))

  override def metrics(value: CompileServerMetrics): Unit =
    publishEvent(Metrics(value))
}
