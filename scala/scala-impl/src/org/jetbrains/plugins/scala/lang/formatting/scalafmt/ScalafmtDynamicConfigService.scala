package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService.ConfigResolveResult
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.ScalafmtResolveError
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtNotifications.FmtVerbosity
import org.scalafmt.dynamic.{ScalafmtReflectConfig, ScalafmtVersion}

trait ScalafmtDynamicConfigService {

  def init(): Unit

  def clearCaches(): Unit

  def configForFileWithTimestamp(
    psiFile: PsiFile,
    verbosity: FmtVerbosity = FmtVerbosity.Verbose,
    resolveFast: Boolean = false
  ): Option[(ScalafmtReflectConfig, Option[Long])]

  def configFileForFile(
    psiFile: PsiFile
  ): Option[VirtualFile]

  final def configForFile(
    psiFile: PsiFile,
    verbosity: FmtVerbosity = FmtVerbosity.Verbose,
    resolveFast: Boolean = false
  ): Option[ScalafmtReflectConfig] =
    configForFileWithTimestamp(psiFile, verbosity, resolveFast).map(_._1)

  def resolveConfigAsync(
    configFile: VirtualFile,
    version: ScalafmtVersion,
    verbosity: FmtVerbosity,
    @RequiresEdt
    onResolveFinished: ConfigResolveResult => Unit
  ): Unit

  def isFileIncludedInProject(file: PsiFile, config: ScalafmtReflectConfig): Boolean

  def isFileIncludedInProject(file: PsiFile): Boolean
}

object ScalafmtDynamicConfigService {

  @NonNls
  val DefaultConfigurationFileName: String = ".scalafmt.conf"

  def apply(project: Project): ScalafmtDynamicConfigService =
    instanceIn(project)

  def instanceIn(project: Project): ScalafmtDynamicConfigService =
    project.getService(classOf[ScalafmtDynamicConfigService])

  def instanceIn(file: PsiElement): ScalafmtDynamicConfigService =
    file.getProject.getService(classOf[ScalafmtDynamicConfigService])

  def isIncludedInProject(file: PsiFile): Boolean =
    instanceIn(file.getProject).isFileIncludedInProject(file)

  type ConfigResolveResult = Either[ConfigResolveError, ScalafmtReflectConfig]

  sealed trait ConfigResolveError

  object ConfigResolveError {
    sealed trait ConfigError extends ConfigResolveError {
      def getMessage: String
    }
    case class ConfigFileNotFound(configPath: String) extends ConfigError {
      override def getMessage: String = s"Scalafmt config file not found: $configPath"
    }
    case class ConfigParseError(configPath: String, cause: Throwable) extends ConfigError {
      override def getMessage: String = cause.getMessage
    }
    case class ConfigCyclicDependenciesError(configPath: String, cause: ConfigCyclicDependencyException) extends ConfigError {
      override def getMessage: String = cause.getMessage
    }
    case class ConfigScalafmtResolveError(error: ScalafmtResolveError) extends ConfigResolveError

    case class ConfigCyclicDependencyException(filesResolveStack: List[VirtualFile]) extends RuntimeException
  }
}
