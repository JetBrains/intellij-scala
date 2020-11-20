package org.jetbrains.jps.incremental.scala
package remote

import java.io._

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.plugins.scala.util.ObjectSerialization

/**
 * @author Pavel Fatin
 */
sealed abstract class Event {
  def toBytes: Array[Byte] =
    ObjectSerialization.toBytes(this)
}

object Event {
  def fromBytes(bytes: Array[Byte]): Event =
    ObjectSerialization.fromBytes(bytes)
}

@SerialVersionUID(-284506638701953916L)
case class MessageEvent(kind: Kind,
                        text: String,
                        source: Option[File],
                        from: PosInfo,
                        to: PosInfo) extends Event

@SerialVersionUID(-6777609711619086870L)
case class ProgressEvent(text: String, done: Option[Float]) extends Event

@SerialVersionUID(7993329544064571495L)
case class DebugEvent(text: String) extends Event

@SerialVersionUID(1L)
case class InfoEvent(text: String) extends Event

@SerialVersionUID(1668649599159817915L)
case class TraceEvent(exceptionClassName: String, message: String, stackTrace: Array[StackTraceElement]) extends Event

@SerialVersionUID(-3155152113364817122L)
case class GeneratedEvent(source: File, module: File, name: String) extends Event

@SerialVersionUID(-7935816100194567870L)
case class DeletedEvent(module: File) extends Event

@SerialVersionUID(-6907017854101285465L)
case class CompilationStartEvent() extends Event

@SerialVersionUID(2848760871163806524L)
case class CompilationEndEvent(sources: Set[File]) extends Event

@SerialVersionUID(1L)
case class ProcessingEndEvent() extends Event

@SerialVersionUID(5572517354322649988L)
case class WorksheetOutputEvent(text: String) extends Event

@SerialVersionUID(1L)
case class CompilationStartedInSbt(path: String) extends Event

@SerialVersionUID(1L)
case class MeteringInfo(info: CompileServerMeteringInfo) extends Event

@SerialVersionUID(1L)
case class Metrics(metrics: CompileServerMetrics) extends Event