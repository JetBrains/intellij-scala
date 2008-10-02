package org.jetbrains.plugins.scala.decompiler

import annotations.Nullable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.vfs.VirtualFile

/**
 * @author ilyas
 */

object DecompilerUtil {

  def isScalaFile(file: VirtualFile): Boolean = {
    if (file.getFileType != StdFileTypes.CLASS) return false
    ScalaDecompiler.isScalaFile(file.contentsToByteArray())
  }


  @Nullable
  def obtainProject : Project = {
    var project: Project = null
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      project = ProjectManager.getInstance().asInstanceOf[ProjectManagerEx].getCurrentTestProject
    } else {
      val projects = ProjectManager.getInstance().getOpenProjects();
      if (projects.length == 0) {
        return null
      }
      project = projects(0)
    }
    project
  }

}