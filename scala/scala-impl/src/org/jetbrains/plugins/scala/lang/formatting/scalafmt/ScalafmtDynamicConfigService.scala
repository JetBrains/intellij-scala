package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.{Project, ProjectUtil}
import com.intellij.openapi.vfs.{StandardFileSystems, VirtualFile}
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.vcsUtil.VcsUtil
import com.typesafe.config.{ConfigException, ConfigFactory}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{inWriteAction, _}
import org.jetbrains.plugins.scala.lang.formatting.OpenFileNotificationActon
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.{DefaultVersion, ScalafmtResolveError, ScalafmtVersion}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtNotifications.FmtVerbosity
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.exceptions.ScalafmtConfigException
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.{ScalafmtDynamicConfig, ScalafmtReflect}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.ScalaCollectionsUtil
import org.jetbrains.sbt.language.SbtFileImpl

import scala.collection.mutable
import scala.util.Try

final class ScalafmtDynamicConfigService(private val project: Project) {

  private val Log = Logger.getInstance(classOf[ScalafmtDynamicConfigService])

  private val configsCache: mutable.Map[String, CachedConfig] = ScalaCollectionsUtil.newConcurrentMap

  def init(): Unit = {
    val scalaSettings = ScalaCodeStyleSettings.getInstance(project)
    val isScalafmtEnabled = scalaSettings.USE_SCALAFMT_FORMATTER
    if (isScalafmtEnabled) {
      val configFile = scalafmtProjectConfigFile(project, scalaSettings.SCALAFMT_CONFIG_PATH)
      val version = configFile.flatMap(readVersion(_).toOption.flatten).getOrElse(DefaultVersion)
      ScalafmtDynamicService.instance.resolveAsync(version, project)
    }
  }

  def clearCaches(): Unit =
    configsCache.clear()

  def resolveConfigAsync(configFile: VirtualFile,
                         version: ScalafmtVersion,
                         verbosity: FmtVerbosity,
                         onResolveFinished: ConfigResolveResult => Unit): Unit = {
    ScalafmtDynamicService.instance.resolveAsync(version, project, _ => {
      refreshFileModificationTimestamp(configFile)
      val configOrError = resolveConfig(configFile, version, verbosity, resolveFast = true)
      onResolveFinished(configOrError)
    })
  }

  private def refreshFileModificationTimestamp(vFile: VirtualFile): Unit = {
    invokeAndWait(inWriteAction(vFile.refresh(false, false)))
  }

  def configForFile(psiFile: PsiFile,
                    verbosity: FmtVerbosity = FmtVerbosity.Verbose,
                    resolveFast: Boolean = false): Option[ScalafmtDynamicConfig] = {
    configForFileWithTimestamp(psiFile, verbosity, resolveFast).map(_._1)
  }

  def configForFileWithTimestamp(psiFile: PsiFile,
                                 verbosity: FmtVerbosity = FmtVerbosity.Verbose,
                                 resolveFast: Boolean = false): Option[(ScalafmtDynamicConfig, Option[Long])] = {
    val settings = CodeStyle.getCustomSettings(psiFile, classOf[ScalaCodeStyleSettings])
    val configPath = settings.SCALAFMT_CONFIG_PATH

    val configFile = scalafmtProjectConfigFile(project, configPath)
    val config = configFile match {
      case Some(file) => resolveConfig(file, DefaultVersion, verbosity, resolveFast).toOption
      case None       => intellijDefaultConfig
    }
    val configWithDialect =
      if (psiFile.isInstanceOf[SbtFileImpl]) {
        config.map(_.withSbtDialect)
      } else {
        config
      }

    val timestamp = configFile.map(_.getPath).flatMap(configsCache.get).map(_.vFileModificationTimestamp)
    configWithDialect.map((_, timestamp))
  }

  private def intellijDefaultConfig: Option[ScalafmtDynamicConfig] = {
    ScalafmtDynamicService.instance
      .resolve(DefaultVersion, downloadIfMissing = false, FmtVerbosity.FailSilent)
      .toOption.map(_.intellijScalaFmtConfig)
  }

  private def resolveConfig(configFile: VirtualFile,
                            defaultVersion: ScalafmtVersion,
                            verbosity: FmtVerbosity,
                            resolveFast: Boolean): ConfigResolveResult = {
    val configPath = configFile.getPath

    val currentVFileTimestamp: Long = configFile.getModificationStamp
    val currentDocTimestamp: Long = inReadAction(FileDocumentManager.getInstance.getDocument(configFile)).getModificationStamp

    val cachedConfig = configsCache.get(configPath)
    cachedConfig match {
      case Some(CachedConfig(config, vFileLastModified, docLastModified))
        if vFileLastModified == currentVFileTimestamp &&
          docLastModified == currentDocTimestamp =>
        Right(config)
      case _ =>
        resolvingConfigWithScalafmt(configFile, defaultVersion, verbosity, resolveFast) match {
          case Right(config) =>
            if (verbosity != FmtVerbosity.Silent) {
              notifyConfigChanges(config, cachedConfig)
            }
            configsCache(configPath) = CachedConfig(config, currentVFileTimestamp, currentDocTimestamp)
            Right(config)
          case Left(error) =>
            if (verbosity == FmtVerbosity.Verbose) {
              reportConfigResolveError(configFile, error)
            }
            Left(error)
        }
    }
  }

  private def resolvingConfigWithScalafmt(configFile: VirtualFile,
                                          defaultVersion: ScalafmtVersion,
                                          verbosity: FmtVerbosity,
                                          resolveFast: Boolean): ConfigResolveResult = {
    val configPath = configFile.getPath
    for {
      _ <- Option(configFile).filter(_.exists).toRight {
        ConfigResolveError.ConfigFileNotFound(configPath)
      }
      version <- readVersion(configFile) match {
        case Right(value) => Right(value.getOrElse(defaultVersion))
        case Left(e)      => Left(ConfigResolveError.ConfigParseError(configPath, e))
      }
      fmtReflect <- ScalafmtDynamicService.instance
        .resolve(version, downloadIfMissing = false, verbosity, resolveFast)
        .left.map(ConfigResolveError.ConfigScalafmtResolveError)
      config <- parseConfig(configFile, fmtReflect)
    } yield config
  }

  private def parseConfig(configFile: VirtualFile, fmtReflect: ScalafmtReflect): ConfigResolveResult = {
    val document = inReadAction(FileDocumentManager.getInstance.getDocument(configFile))
    val configText = document.getText()
    Try(fmtReflect.parseConfigFromString(configText)).toEither.left.map {
      case e: ScalafmtConfigException =>
        ConfigResolveError.ConfigParseError(configFile.getPath, e)
      case e =>
        Log.error(e)
        ConfigResolveError.UnknownError(e)
    }
  }

  private def notifyConfigChanges(config: ScalafmtDynamicConfig, cachedConfig: Option[CachedConfig]): Unit = {
    val contentChanged = !cachedConfig.map(_.config).contains(config)
    if (contentChanged) {
      ScalafmtNotifications.displayInfo(ScalaBundle.message("scalafmt.picked.new.config", config.version))
    }
  }

  private def reportConfigResolveError(configFile: VirtualFile, configError: ConfigResolveError): Unit = {
    import ConfigResolveError._
    val details = configError match {
      case ConfigFileNotFound(_)           => ScalaBundle.message("scalafmt.config.load.errors.file.not.found")
      case ConfigParseError(_, cause)      => ScalaBundle.message("scalafmt.config.load.errors.parse.error", cause.getMessage)
      case UnknownError(unknownMessage, _) => ScalaBundle.message("scalafmt.config.load.errors.unknown.error", unknownMessage)
      /** reported in [[org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.reportResolveError]] */
      case _: ConfigScalafmtResolveError   => return
    }
    val errorMessage = ScalaBundle.message("scalafmt.config.load.errors.failed.to.load.config") + ":<br>" + details
    // NOTE: ConfError does not provide any information about parsing error position, so setting offset to zero
    val openFileAction = new OpenFileNotificationActon(project, configFile, offset = 0, title = ScalaBundle.message("scalafmt.config.load.actions.open.config.file"))
    ScalafmtNotifications.displayWarning(errorMessage, Seq(openFileAction))
  }

  def isIncludedInProject(file: PsiFile, config: ScalafmtDynamicConfig): Boolean = {
    file.getVirtualFile.toOption match {
      case Some(vFile) => config.isIncludedInProject(vFile.getPath)
      case None => true // in-memory files can't be in scalafmt config exclude rules, they should always be formatted
    }
  }

  def isIncludedInProject(file: PsiFile): Boolean = {
    val configOpt = configForFile(file)
    configOpt.exists(isIncludedInProject(file, _))
  }
}

object ScalafmtDynamicConfigService {
  @NonNls
  val DefaultConfigurationFileName: String = ".scalafmt.conf"

  def instanceIn(project: Project): ScalafmtDynamicConfigService =
    project.getService(classOf[ScalafmtDynamicConfigService])

  def instanceIn(file: PsiElement): ScalafmtDynamicConfigService =
    file.getProject.getService(classOf[ScalafmtDynamicConfigService])

  def isIncludedInProject(file: PsiFile, config: ScalafmtDynamicConfig): Boolean =
    instanceIn(file.getProject).isIncludedInProject(file, config)

  def isIncludedInProject(file: PsiFile): Boolean =
    instanceIn(file.getProject).isIncludedInProject(file)

  def readVersion(configFile: VirtualFile): Either[Throwable, Option[String]] = {
    Try {
      val document = inReadAction(FileDocumentManager.getInstance.getDocument(configFile))
      val config = ConfigFactory.parseString(document.getText)
      Option(config.getString("version").trim)
    }.toEither.left.flatMap {
      case _: ConfigException.Missing => Right(None)
      case e => Left(e)
    }
  }

  type ConfigResolveResult = Either[ConfigResolveError, ScalafmtDynamicConfig]

  sealed trait ConfigResolveError

  object ConfigResolveError {
    sealed trait ConfigError extends ConfigResolveError
    case class ConfigFileNotFound(configPath: String) extends ConfigError
    case class ConfigParseError(configPath: String, cause: Throwable) extends ConfigError
    case class ConfigScalafmtResolveError(error: ScalafmtResolveError) extends ConfigResolveError
    case class UnknownError(message: String, cause: Option[Throwable]) extends ConfigResolveError

    object UnknownError {
      def apply(cause: Throwable): UnknownError = new UnknownError(cause.getMessage, Option(cause))
    }
  }

  private case class CachedConfig(config: ScalafmtDynamicConfig,
                                  vFileModificationTimestamp: Long,
                                  docModificationTimestamp: Long)

  def scalafmtProjectConfigFile(project: Project, configPath: String): Option[VirtualFile] = {
    val configPathActual = if (configPath.nonEmpty) configPath else DefaultConfigurationFileName
    scalafmtProjectConfigFileNonEmpty(project, configPathActual)
  }

  private def scalafmtProjectConfigFileNonEmpty(project: Project, configPath: String): Option[VirtualFile] =
    absolutePathFromConfigPath(project, configPath).flatMap { path =>
      StandardFileSystems.local.findFileByPath(path).toOption
    }

  def absolutePathFromConfigPath(project: Project, configPath: String): Option[String] = {
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
