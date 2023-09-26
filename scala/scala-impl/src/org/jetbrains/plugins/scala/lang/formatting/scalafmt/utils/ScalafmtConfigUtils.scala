package org.jetbrains.plugins.scala.lang.formatting.scalafmt.utils

import com.intellij.openapi.project.{Project, ProjectUtil}
import com.intellij.openapi.vfs.{StandardFileSystems, VirtualFile}
import com.intellij.vcsUtil.VcsUtil
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService.DefaultConfigurationFileName

object ScalafmtConfigUtils {

  def actualConfigPath(configPath: String): String =
    if (StringUtils.isNotBlank(configPath)) configPath else DefaultConfigurationFileName

  def projectConfigFile(project: Project, configPath: String): Option[VirtualFile] = {
    val actualPath = actualConfigPath(configPath)
    projectConfigFileImpl(project, actualPath)
  }

  private def projectConfigFileImpl(project: Project, configPath: String): Option[VirtualFile] =
    projectConfigFileAbsolutePath(project, configPath).flatMap { path =>
      StandardFileSystems.local.findFileByPath(path).toOption
    }

  def projectConfigFileAbsolutePath(project: Project, configPath: String): Option[String] = {
    val isRelativePath = !isAbsolutePath(configPath)
    if (project.isDefault) {
      if (isRelativePath) None
      else Some(configPath)
    } else {
      def absolutePath(baseDir: VirtualFile): String = {
        val prefix = if (isRelativePath) baseDir.getCanonicalPath + "/" else ""
        prefix + configPath
      }

      val projectRoot = ProjectUtil.guessProjectDir(project).toOption
      projectRoot.flatMap { baseDir =>
        val result = absolutePath(baseDir)
        if (exists(result) || !isRelativePath) {
          Some(result)
        } else {
          //SCL-16273
          val vcsRoot = VcsUtil.getVcsRootFor(project, baseDir).toOption
          val vcsResult = vcsRoot.map(absolutePath).filter(exists)
          vcsResult.orElse(Some(result))
        }
      }
    }
  }

  def resolveConfigIncludeFile(baseConfig: VirtualFile, includeConfig: String): Option[VirtualFile] = {
    val baseDir      = baseConfig.getParent match {
      case null => return None
      case parent => parent
    }
    val prefix = if (isAbsolutePath(includeConfig)) "" else baseDir.getCanonicalPath + "/"
    val absolutePath = prefix + includeConfig
    Option(StandardFileSystems.local.findFileByPath(absolutePath))
  }

  private def exists(path: String): Boolean =
    StandardFileSystems.local.findFileByPath(path) != null

  private def isAbsolutePath(s: String): Boolean =
    s.startsWith("/") ||
      s.startsWith("\\") ||
      s.matches("""[\\/]?\w:[\\/].*""") // windows-style, e.g. C:/path/... /c:/path/... C:\path\...
}
