package org.jetbrains.jps.incremental.scala

import java.io.File

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.remote.{CompileServerMeteringInfo, CompileServerMetrics}

class DummyClient extends Client {
  override def message(msg: Client.ClientMsg): Unit = ()
  override def deleted(module: File): Unit = ()
  override def progress(text: String, done: Option[Float]): Unit = ()
  override def isCanceled: Boolean = false
  override def internalInfo(text: String): Unit = ()
  override def internalDebug(text: String): Unit = ()
  override def trace(exception: Throwable): Unit = ()
  override def generated(source: File, module: File, name: String): Unit = ()
  override def worksheetOutput(text: String): Unit = ()
  override def compilationStart(): Unit = ()
  override def compilationEnd(sources: Set[File]): Unit = ()
  override def processingEnd(): Unit = ()
  override def sourceStarted(source: String): Unit = ()
  override def meteringInfo(info: CompileServerMeteringInfo): Unit = ()
  override def metrics(value: CompileServerMetrics): Unit = ()
}
