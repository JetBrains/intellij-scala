package org.jetbrains.jps.incremental.scala
package remote

import java.io._
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind

/**
 * @author Pavel Fatin
 */
sealed abstract class Event {
  def asString: String = {
    val buffer = new ByteArrayOutputStream()
    val stream = new ObjectOutputStream(buffer)
    stream.writeObject(this)
    stream.close()
    new String(buffer.toByteArray)
  }
}

object Event {
  def from(string: String): Event = {
    val buffer = new ByteArrayInputStream(string.getBytes)
    val stream = new ObjectInputStream(buffer)
    val event = stream.readObject().asInstanceOf[Event]
    stream.close()
    event
  }
}

case class MessageEvent(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]) extends Event

case class TraceEvent(exception: Throwable) extends Event

case class ProgressEvent(text: String, done: Option[Float]) extends Event

case class GeneratedEvent(source: File, module: File, name: String) extends Event