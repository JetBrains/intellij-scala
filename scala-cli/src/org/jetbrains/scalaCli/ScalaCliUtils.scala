package org.jetbrains.scalaCli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.bsp.project.BspExternalSystemUtil

import java.io.File
import scala.util.Try

object ScalaCliUtils {

  private val BspServerName = "scala-cli"

  def isBspScalaCliProject(project: Project): Boolean = {
    val projectData = BspExternalSystemUtil.getBspProjectData(project)
    projectData.exists(_.serverDisplayName == BspServerName)
  }

  def isScalaCliInstalled(workspace: File): Boolean =
    Try {
      val generalCommandLine = new GeneralCommandLine(getScalaCliCommand, "version")
        .withWorkDirectory(workspace)
      val process = generalCommandLine.toProcessBuilder.start()
      val exitValue = process.waitFor()
      exitValue == 0
    }.getOrElse(false)

  /**
   * If these are tests, the Scala CLI is not installed globally - the script is only available in the project root directory,
   * so for this reason we have to change the way it is called
   */
  def getScalaCliCommand: String =
    if (ApplicationManager.getApplication.isUnitTestMode) "./scala-cli"
    else "scala-cli"
}
