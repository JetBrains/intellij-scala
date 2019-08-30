package org.jetbrains.plugins.scala
package util

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

  def isInScala3(file: Option[PsiFile]): Boolean = file.exists {
    _.isInScala3Module
  }

  def isTrailingCommasEnabled(file: PsiFile): Boolean = Option(file).exists { file =>
    val (result, _) = areTrailingCommasAndIdBindingEnabled(Some(file))(file.getProject)
    result
  }

  def areTrailingCommasAndIdBindingEnabled(file: Option[PsiFile])
                                          (implicit project: Project): (Boolean, Boolean) = {
    val enabledPredicate = isEnabledIn(file)

    import ScalaProjectSettings.TrailingCommasMode._
    val trailingCommas = ScalaProjectSettings.getInstance(project).getTrailingCommasMode match {
      case Enabled => true
      case Disabled => false
      case Auto => enabledPredicate("2.12.2")
    }

    (trailingCommas, enabledPredicate("2.12"))
  }

  private def isEnabledIn(maybeFile: Option[PsiFile]): String => Boolean =
    maybeFile.flatMap {
      case file if applicationUnitTestModeEnabled && file.getVirtualFile.isInstanceOf[LightVirtualFileBase] =>
        Some(Function.const(true)(_: String))
      case file =>
        for {
          module <- file.module
          sdk <- module.scalaSdk
          presentation <- sdk.compilerVersion

          version = Version(presentation)
        } yield version >= Version(_: String)
    }.getOrElse(Function.const(false)(_))
}