package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.compiler.data.IncrementalityType

import java.nio.file.Path

private final class ServerInstance(
  val watcher: ProcessWatcher,
  val port: Int,
  val workingDir: Option[Path],
  val jdk: JDK,
  val jvmParameters: Set[String],
  val jpsUseUnifiedIC: Boolean,
  val incrementalCompiler: IncrementalityType
) extends CompileServerManager.ErrorListener {

  private var _stopped: Boolean = false
  private val errorBuffer: StringBuffer = new StringBuffer()

  def running: Boolean = !_stopped && watcher.running

  def stopped: Boolean = _stopped

  def pid: Long = watcher.pid

  def destroyAndWait(): Boolean = {
    _stopped = true
    watcher.destroyAndWait()
  }

  def destroyAndWaitFor(timeoutMs: Long): Boolean = {
    _stopped = true
    watcher.destroyAndWaitFor(timeoutMs)
  }

  def summary: String = {
    s"pid: $pid" +
      s", port: $port" +
      s", jdk: $jdk" +
      s", jvmParameters: ${jvmParameters.mkString(",")}" +
      s", jpsUseUnifiedIC: $jpsUseUnifiedIC" +
      s", incrementalCompiler: $incrementalCompiler" +
      s", stopped: ${_stopped}" +
      s", running: $running" +
      s", errors: ${errorBuffer.toString}"
  }

  override def onError(text: String): Unit =
    errorBuffer.append(text)
}
