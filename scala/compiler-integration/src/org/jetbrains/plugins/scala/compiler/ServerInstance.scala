package org.jetbrains.plugins.scala.compiler

import java.io.File

private final class ServerInstance(
  val watcher: ProcessWatcher,
  val port: Int,
  val workingDir: File,
  val jdk: JDK,
  val jvmParameters: Set[String]
) extends CompileServerManager.ErrorListener {

  private var _stopped: Boolean = false
  private val errorBuffer: StringBuffer = new StringBuffer()

  def running: Boolean = !_stopped && watcher.running

  def stopped: Boolean = _stopped

  def pid: Long = watcher.pid

  def destroyAndWait(timeoutMs: Long): Boolean = {
    _stopped = true
    watcher.destroyAndWait(timeoutMs)
  }

  def summary: String = {
    s"pid: $pid" +
      s", port: $port" +
      s", jdk: $jdk" +
      s", jvmParameters: ${jvmParameters.mkString(",")}" +
      s", stopped: ${_stopped}" +
      s", running: $running" +
      s", errors: ${errorBuffer.toString}"
  }

  override def onError(text: String): Unit =
    errorBuffer.append(text)
}
