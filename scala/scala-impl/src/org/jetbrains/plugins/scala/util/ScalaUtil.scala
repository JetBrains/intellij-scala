package org.jetbrains.plugins.scala
package util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFileBase
import com.intellij.util.PathUtil
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

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

  def isTrailingCommasDisabled(file: PsiFile): Boolean = file match {
    case null => true
    case _ => !isTrailingCommasEnabled(Some(file)) {
      findScalaVersion(file)
    }(file.getProject)
  }

  def isTrailingCommasEnabled(file: Option[PsiFile])
                             (actualVersion: => Option[Version])
                             (implicit project: Project): Boolean = {
    import ScalaProjectSettings.TrailingCommasMode._
    ScalaProjectSettings.getInstance(project).getTrailingCommasMode match {
      case Enabled => true
      case Disabled => false
      case Auto => isEnabledIn(file, "2.12.2")(actualVersion)
    }
  }

  def isIdBindingEnabled(file: Option[PsiFile])
                        (actualVersion: => Option[Version]): Boolean =
    isEnabledIn(file, "2.12")(actualVersion)

  def findScalaVersion(file: PsiFile): Option[Version] =
    getScalaVersion(file).map(Version(_))

  private def isEnabledIn(file: Option[PsiFile], requiredVersion: String)
                         (actualVersion: => Option[Version]): Boolean =
    (ApplicationManager.getApplication.isUnitTestMode &&
      file.exists(_.getVirtualFile.isInstanceOf[LightVirtualFileBase])) ||
      actualVersion.exists(_ >= Version(requiredVersion))
}