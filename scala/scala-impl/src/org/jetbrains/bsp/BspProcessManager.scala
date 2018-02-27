package org.jetbrains.bsp

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import org.jetbrains.bsp.BspProcessManager.BspConnection
import org.jetbrains.sbt.shell.SbtProcessManager.ProcessData

class BspProcessManager(project: Project) extends AbstractProjectComponent(project) {

  @volatile private var processData: Option[BspConnection] = None

  def start: Option[BspConnection] = ???

  def discover: Option[BspConnection] = ???




}

object BspProcessManager {

  case class BspConnection()
}
