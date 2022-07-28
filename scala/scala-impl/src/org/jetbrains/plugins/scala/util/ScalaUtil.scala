package org.jetbrains.plugins.scala
package util

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.project._

object ScalaUtil {

  def getScalaVersion(file: PsiFile): Option[String] = file match {
    case ScFile.VirtualFile(virtualFile) =>
      getModuleForFile(virtualFile)(file.getProject)
        .flatMap(_.scalaSdk)
        .flatMap(_.libraryVersion)
    case _ => None
  }

  def getModuleForFile(file: VirtualFile)
                      (implicit project: Project): Option[Module] = Option {
    ProjectRootManager.getInstance(project).getFileIndex.getModuleForFile(file)
  }
}