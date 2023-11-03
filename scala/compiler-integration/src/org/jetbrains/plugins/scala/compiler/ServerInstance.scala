package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.project.Project

import java.io.File

private final class ServerInstance(
  val project: Project,
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
      s", stopped: ${_stopped}" +
      s", running: $running" +
      s", errors: ${errorBuffer.toString}"
  }

  override def onError(text: String): Unit =
    errorBuffer.append(text)
}
