package org.jetbrains.jps.incremental.scala
package remote

/**
 * @author Pavel Fatin
 * @see [[org.jetbrains.jps.incremental.scala.remote.EventGeneratingClient]]
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

      case TraceEvent(exceptionClassName, message, stackTrace) =>
        client.trace(new ServerException(exceptionClassName, message, stackTrace))

      case GeneratedEvent(source, module, name) =>
        client.generated(source, module, name)

      case DeletedEvent(module) =>
        client.deleted(module)

      case SourceProcessedEvent(source) =>
        client.processed(source)

      case CompilationEndEvent() =>
        client.compilationEnd()

      case ProcessingEndEvent() =>
        client.processingEnd()

      case WorksheetOutputEvent(text) =>
        client.worksheetOutput(text)

      case CompilationStartedInSbt(file) =>
        client.sourceStarted(file)
    }
  }
}

// field is called `stackTraceElements` not to confuse with `Throwable.stackTrace`
private class ServerException(exceptionClassName: String,
                              message: String,
                              stackTraceElements: Array[StackTraceElement]) extends Exception(message, null) {

  setStackTrace(stackTraceElements)

  override def toString: String = {
    val message = getLocalizedMessage
    val reason = if (message != null) s": $message" else ""
    s"$exceptionClassName$reason"
  }
}