package org.jetbrains.jps.incremental.scala

import java.io.{PrintStream, PrintWriter}

import org.jetbrains.jps.incremental.scala.remote._

/**
 * @author Pavel Fatin
 */
class ClientEventProcessor(client: Client) {
  def process(event: Event) {
    event match {
      case MessageEvent(kind, text, source, line, column) =>
        client.message(kind, text, source, line, column)

      case ProgressEvent(text, done) =>
        client.progress(text, done)

      case DebugEvent(text) =>
        client.debug(text)

      case TraceEvent(message, lines) =>
        client.trace(new ServerException(message, lines))

      case GeneratedEvent(source, module, name) =>
        client.generated(source, module, name)

      case DeletedEvent(module) =>
        client.deleted(module)

      case SourceProcessedEvent(source) =>
        client.processed(source)

      case CompilationEndEvent() =>
        client.compilationEnd()

      case WorksheetOutputEvent(text) => 
        client.worksheetOutput(text)
    }
  }
}

class ServerException(message: String, lines: Array[String]) extends Exception {
  override def getMessage = message

  override def printStackTrace(s: PrintWriter) {
    lines.foreach(s.println)
  }

  override def printStackTrace(s: PrintStream) {
    lines.foreach(s.println)
  }
}
