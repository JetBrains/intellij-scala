package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.server.CompileServerPort

import java.nio.file.Path

private final class ServerInstance(
  val watcher: ProcessWatcher,
  val createdAtStackTrace: Throwable,
  val compileServerSystemDir: Path,
  val port: CompileServerPort,
  val workingDir: Option[Path],
  val jdk: JDK,
  val jvmParameters: Set[String],
  val jpsUseUnifiedIC: Boolean,
  val incrementalCompiler: IncrementalityType
) {

  private var _stopped: Boolean = false

  def running: Boolean = !_stopped && watcher.running

  def stopped: Boolean = _stopped

  def pid: String = watcher.pid.fold("N/A")(_.toString)

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
      s", running: $running"
  }
}
