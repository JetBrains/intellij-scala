package org.jetbrains.plugins.scala
package util

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.PathUtil
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.project._

/**
 * User: Alexander Podkhalyuzin
 * Date: 16.11.11
 */
object ScalaUtil {

  def runnersPath(): String = {
    PathUtil.getJarPathForClass(classOf[Client]).replace("compiler-shared", "runners")
  }

  def getScalaVersion(file: PsiFile): Option[String] = file match {
    case ScFile.VirtualFile(virtualFile) =>
      getModuleForFile(virtualFile)(file.getProject)
        .flatMap(_.scalaSdk)
        .flatMap(_.compilerVersion)
    case _ => None
  }

  def getModuleForFile(file: VirtualFile)
                      (implicit project: Project): Option[Module] = Option {
    ProjectRootManager.getInstance(project).getFileIndex.getModuleForFile(file)
  }
}