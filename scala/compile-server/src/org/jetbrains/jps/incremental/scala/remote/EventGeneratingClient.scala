package org.jetbrains.jps.incremental.scala
package remote

import org.jetbrains.annotations.Nls
import sbt.internal.inc.CompileFailed

import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * @see `org.jetbrains.jps.incremental.scala.ClientEventProcessor`
 */
class EventGeneratingClient(writeEvent: Event => Unit, canceled: => Boolean) extends Client with AutoCloseable {

  private val eventGenerator = new AsynchEventGenerator(writeEvent)

  private def publishEvent(event: Event): Unit =
    eventGenerator.listener(event)

  override def close(): Unit =
    eventGenerator.complete(20, TimeUnit.MINUTES)

  override def isCanceled: Boolean = canceled

  override def message(msg: Client.ClientMsg): Unit = {
    val Client.ClientMsg(kind, text, source, pointer, problemStart, problemEnd, diagnostics) = msg
    publishEvent(MessageEvent(kind, text, source, pointer, problemStart, problemEnd, diagnostics))
  }

  override def trace(exception: Throwable): Unit = {
    val message = exception match {
      case cf: CompileFailed => cf.toString // CompileFailed always has null message
      case _ => exception.getMessage
    }
    publishEvent(TraceEvent(exception.getClass.getName, message, exception.getStackTrace))
  }

  override def progress(@Nls text: String, done: Option[Float]): Unit =
    publishEvent(ProgressEvent(text, done))

  override def internalInfo(text: String): Unit =
    publishEvent(InternalInfoEvent(text))

  override def internalDebug(text: String): Unit =
    publishEvent(InternalDebugEvent(text))

  override def internalTrace(text: String): Unit =
    publishEvent(InternalTraceEvent(text))

  override def generated(source: Path, module: Path, name: String): Unit =
    publishEvent(GeneratedEvent(SerializablePath(source), SerializablePath(module), name))

  override def deleted(module: Path): Unit =
    publishEvent(DeletedEvent(SerializablePath(module)))

  override def compilationStart(): Unit =
    publishEvent(CompilationStartEvent())

  override def compilationPhase(phase: String): Unit =
    publishEvent(CompilationPhaseEvent(phase))

  override def compilationUnit(path: String): Unit =
    publishEvent(CompilationUnitEvent(path))

  override def compilationEnd(sources: Set[Path]): Unit =
    publishEvent(CompilationEndEvent(sources.map(SerializablePath(_))))

  override def processingEnd(): Unit =
    publishEvent(ProcessingEndEvent())

  override def worksheetOutput(text: String): Unit =
    publishEvent(WorksheetOutputEvent(text))

  override def sourceStarted(source: String): Unit =
    publishEvent(CompilationStartedInSbtEvent(source))

  override def metrics(value: CompileServerMetrics): Unit =
    publishEvent(MetricsEvent(value))
}
