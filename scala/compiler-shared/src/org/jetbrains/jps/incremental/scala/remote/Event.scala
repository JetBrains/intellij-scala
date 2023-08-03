package org.jetbrains.jps.incremental.scala
package remote

import org.jetbrains.annotations.Nls
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.plugins.scala.util.ObjectSerialization

import java.io._

sealed abstract class Event {
  def toBytes: Array[Byte] =
    ObjectSerialization.toBytes(this)
}

object Event {
  def fromBytes(bytes: Array[Byte]): Event =
    ObjectSerialization.fromBytes(bytes)
}

@SerialVersionUID(2L)
case class MessageEvent(kind: MessageKind,
                        @Nls text: String,
                        source: Option[File],
                        pointer: Option[PosInfo],
                        problemStart: Option[PosInfo],
                        problemEnd: Option[PosInfo]) extends Event

@SerialVersionUID(-6777609711619086870L)
case class ProgressEvent(@Nls text: String, done: Option[Float]) extends Event

@SerialVersionUID(3L)
case class InternalInfoEvent(text: String) extends Event

@SerialVersionUID(3L)
case class InternalDebugEvent(text: String) extends Event

@SerialVersionUID(3L)
case class InternalTraceEvent(text: String) extends Event

@SerialVersionUID(1668649599159817915L)
case class TraceEvent(exceptionClassName: String, message: String, stackTrace: Array[StackTraceElement]) extends Event

@SerialVersionUID(-3155152113364817122L)
case class GeneratedEvent(source: File, module: File, name: String) extends Event

@SerialVersionUID(-7935816100194567870L)
case class DeletedEvent(module: File) extends Event

@SerialVersionUID(-6907017854101285465L)
case class CompilationStartEvent() extends Event

@SerialVersionUID(-6907027214131285462L)
case class CompilationPhaseEvent(name: String) extends Event

@SerialVersionUID(-3907022214131225432L)
case class CompilationUnitEvent(path: String) extends Event

@SerialVersionUID(2848760871163806524L)
case class CompilationEndEvent(sources: Set[File]) extends Event

@SerialVersionUID(1L)
case class ProcessingEndEvent() extends Event

@SerialVersionUID(5572517354322649988L)
case class WorksheetOutputEvent(text: String) extends Event

@SerialVersionUID(1L)
case class CompilationStartedInSbtEvent(path: String) extends Event

@SerialVersionUID(1L)
case class MetricsEvent(metrics: CompileServerMetrics) extends Event
