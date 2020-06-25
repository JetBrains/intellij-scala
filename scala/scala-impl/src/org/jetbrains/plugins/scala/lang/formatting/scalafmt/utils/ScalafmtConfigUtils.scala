package org.jetbrains.plugins.scala.lang.formatting.scalafmt.utils

import com.intellij.openapi.project.{Project, ProjectUtil}
import com.intellij.openapi.vfs.{StandardFileSystems, VirtualFile}
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService.DefaultConfigurationFileName

object ScalafmtConfigUtils {

  def projectConfigFile(project: Project, configPath: String): Option[VirtualFile] = {
    val configPathActual = if (configPath.nonEmpty) configPath else DefaultConfigurationFileName
    projectConfigFileImpl(project, configPathActual)
  }

  private def projectConfigFileImpl(project: Project, configPath: String): Option[VirtualFile] =
    projectConfigFileAbsolutePath(project, configPath).flatMap { path =>
      StandardFileSystems.local.findFileByPath(path).toOption
    }

  def projectConfigFileAbsolutePath(project: Project, configPath: String): Option[String] = {
    val isRelativePath = configPath.startsWith(".")
    if (project.isDefault) {
      if (isRelativePath) None
      else Some(configPath)
    } else {
      def absolutePath(baseDir: VirtualFile): String = {
        val prefix = if (isRelativePath) baseDir.getCanonicalPath + "/" else ""
        prefix + configPath
      }

      def exists(path: String): Boolean = StandardFileSystems.local.findFileByPath(path) != null

      val projectRoot = ProjectUtil.guessProjectDir(project).toOption
      projectRoot.flatMap { baseDir =>
        val result = absolutePath(baseDir)
        if (exists(result) || !isRelativePath) {
          Some(result)
        } else {
          val vcsRoot = VcsUtil.getVcsRootFor(project, baseDir).toOption
          val vcsResult = vcsRoot.map(absolutePath).filter(exists)
          vcsResult.orElse(Some(result))
        }
      }
    }
  }
}
