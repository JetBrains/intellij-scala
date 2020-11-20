package org.jetbrains.jps.incremental.scala
import java.io.File

import org.jetbrains.jps.incremental.scala.remote.{CompileServerMeteringInfo, CompileServerMetrics}

class DelegateClient(client: Client)
  extends Client {

  override def message(msg: Client.ClientMsg): Unit =
    client.message(msg)

  override def trace(exception: Throwable): Unit =
    client.trace(exception)

  override def progress(text: String, done: Option[Float]): Unit =
    client.progress(text, done)

  override def internalInfo(text: String): Unit =
    client.internalInfo(text)

  override def internalDebug(text: String): Unit =
    client.internalDebug(text)

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
  
  override def compilationEnd(sources: Set[File]): Unit =
    client.compilationEnd(sources)

  override def processingEnd(): Unit =
    client.processingEnd()

  override def sourceStarted(source: String): Unit =
    client.sourceStarted(source)

  override def meteringInfo(info: CompileServerMeteringInfo): Unit =
    client.meteringInfo(info)

  override def metrics(value: CompileServerMetrics): Unit =
    client.metrics(value)
}
