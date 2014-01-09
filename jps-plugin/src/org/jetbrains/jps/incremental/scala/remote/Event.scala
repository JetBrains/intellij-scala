package org.jetbrains.jps.incremental.scala
package remote

import java.io._
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import com.intellij.openapi.util.io.FileUtil

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

case class MessageEvent(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]) extends Event

case class ProgressEvent(text: String, done: Option[Float]) extends Event

case class DebugEvent(text: String) extends Event

case class TraceEvent(message: String, lines: Array[String]) extends Event

case class GeneratedEvent(source: File, module: File, name: String) extends Event

case class DeletedEvent(module: File) extends Event

case class SourceProcessedEvent(source: File) extends Event