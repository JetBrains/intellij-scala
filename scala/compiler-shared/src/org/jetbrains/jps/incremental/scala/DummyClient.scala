package org.jetbrains.jps.incremental.scala

import org.jetbrains.annotations.Nls
import org.jetbrains.jps.incremental.scala.remote.CompileServerMetrics

import java.nio.file.Path

class DummyClient extends Client {
  override def message(msg: Client.ClientMsg): Unit = ()
  override def deleted(module: Path): Unit = ()
  override def progress(@Nls text: String, done: Option[Float]): Unit = ()
  override def isCanceled: Boolean = false
  override def internalInfo(text: String): Unit = ()
  override def internalDebug(text: String): Unit = ()
  override def internalTrace(text: String): Unit = ()
  override def trace(exception: Throwable): Unit = ()
  override def generated(source: Path, module: Path, name: String): Unit = ()
  override def worksheetOutput(text: String): Unit = ()
  override def compilationStart(): Unit = ()
  override def compilationPhase(name: String): Unit = ()
  override def compilationUnit(path: String): Unit = ()
  override def compilationEnd(sources: Set[Path]): Unit = ()
  override def processingEnd(): Unit = ()
  override def sourceStarted(source: String): Unit = ()
  override def metrics(value: CompileServerMetrics): Unit = ()
}

object DummyClient {
  val Instance: DummyClient = new DummyClient
}
