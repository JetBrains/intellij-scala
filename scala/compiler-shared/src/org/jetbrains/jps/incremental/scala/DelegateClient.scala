package org.jetbrains.jps.incremental.scala

import org.jetbrains.annotations.Nls
import org.jetbrains.jps.incremental.scala.remote.CompileServerMetrics

import java.io.File

class DelegateClient(client: Client)
  extends Client {

  override def message(msg: Client.ClientMsg): Unit =
    client.message(msg)

  override def trace(exception: Throwable): Unit =
    client.trace(exception)

  override def progress(@Nls text: String, done: Option[Float]): Unit =
    client.progress(text, done)

  override def internalInfo(text: String): Unit =
    client.internalInfo(text)

  override def internalDebug(text: String): Unit =
    client.internalDebug(text)

  override def internalTrace(text: String): Unit =
    client.internalTrace(text)

  override def generated(source: File, module: File, name: String): Unit =
    client.generated(source, module, name)

  override def deleted(module: File): Unit =
    client.deleted(module)

  override def isCanceled: Boolean =
    client.isCanceled

  override def worksheetOutput(text: String): Unit =
    client.worksheetOutput(text)

  override def compilationStart(): Unit =
    client.compilationStart()
  
  override def compilationPhase(name: String): Unit =
    client.compilationPhase(name)

  override def compilationUnit(path: String): Unit =
    client.compilationUnit(path)

  override def compilationEnd(sources: Set[File]): Unit =
    client.compilationEnd(sources)

  override def processingEnd(): Unit =
    client.processingEnd()

  override def sourceStarted(source: String): Unit =
    client.sourceStarted(source)

  override def metrics(value: CompileServerMetrics): Unit =
    client.metrics(value)
}
