package org.jetbrains.jps.incremental.scala
package remote

import java.io._

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind

/**
 * @author Pavel Fatin
 */
sealed abstract class Event {
  def toBytes: Array[Byte] = {
    val buffer = new ByteArrayOutputStream()
    val stream = new ObjectOutputStream(buffer)
    stream.writeObject(this)
    stream.close()
    buffer.toByteArray
  }
}

object Event {
  def fromBytes(bytes: Array[Byte]): Event = {
    val buffer = new ByteArrayInputStream(bytes)
    val stream = new ObjectInputStream(buffer)
    val event = stream.readObject().asInstanceOf[Event]
    if (stream.available > 0) {
      val excess = FileUtil.loadTextAndClose(stream)
      throw new IllegalArgumentException("Excess bytes after event deserialization: " + excess)
    }
    stream.close()
    event
  }
}

@SerialVersionUID(1317094340928824239L)
case class MessageEvent(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]) extends Event

@SerialVersionUID(-6777609711619086870L)
case class ProgressEvent(text: String, done: Option[Float]) extends Event

@SerialVersionUID(7993329544064571494L)
case class DebugEvent(text: String) extends Event

@SerialVersionUID(-7446958700174538313L)
case class TraceEvent(message: String, lines: Array[String]) extends Event

@SerialVersionUID(-3155152113364817122L)
case class GeneratedEvent(source: File, module: File, name: String) extends Event

@SerialVersionUID(-7935816100194567870L)
case class DeletedEvent(module: File) extends Event

@SerialVersionUID(3581751389645846347L)
case class SourceProcessedEvent(source: File) extends Event

@SerialVersionUID(-2795117544723203396L)
case class CompilationEndEvent() extends Event

@SerialVersionUID(5572517354322649988L)
case class WorksheetOutputEvent(text: String) extends Event