package org.jetbrains.scalaCli

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.bsp.project.BspExternalSystemUtil

import java.io.File
import scala.sys.process.{Process, ProcessLogger}
import scala.util.Try

object ScalaCliUtils {

  private val BspServerName = "scala-cli"

  def isBspScalaCliProject(project: Project): Boolean = {
    val projectData = BspExternalSystemUtil.getBspProjectData(project)
    projectData.exists(_.serverDisplayName == BspServerName)
  }

  def isScalaCliInstalled(workspace: File): Boolean = {
    val command = s"$getScalaCliCommand version"
    val work = Try(Process(command, workspace)! ProcessLogger(_ => (), _ => ()))
    work.containsZero
  }

  implicit class TryIntOps(result: Try[Int]) {
    def containsZero: Boolean =
      result.map(_ == 0).getOrElse(false)
  }

  /**
   * If these are tests, the Scala CLI is not installed globally - the script is only available in the project root directory,
   * so for this reason we have to change the way it is called
   */
  def getScalaCliCommand: String =
    if (ApplicationManager.getApplication.isUnitTestMode) "./scala-cli"
    else "scala-cli"
}
