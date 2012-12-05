package org.jetbrains.jps.incremental.scala
package remote

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

      case GeneratedEvent(source, module, name) =>
        client.generated(source, module, name)

      case TraceEvent(exception) =>
        client.trace(exception)
    }
  }
}
