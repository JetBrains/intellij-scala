package org.jetbrains.scalaCli.bsp

import com.intellij.openapi.project.Project
import org.jetbrains.bsp.project.BspExternalSystemUtil

object ScalaCliBspUtils {

  private val BspServerName = "scala-cli"

  def isBspScalaCliProject(project: Project): Boolean = {
    val projectData = BspExternalSystemUtil.getBspProjectData(project)
    projectData.exists(_.serverDisplayName == BspServerName)
  }
}
