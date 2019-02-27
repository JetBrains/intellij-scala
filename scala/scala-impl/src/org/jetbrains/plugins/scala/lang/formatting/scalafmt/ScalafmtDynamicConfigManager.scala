package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{StandardFileSystems, VirtualFile}
import com.intellij.psi.PsiFile
import com.typesafe.config.{ConfigException, ConfigFactory}
import org.jetbrains.plugins.scala.extensions.{inWriteAction, _}
import org.jetbrains.plugins.scala.lang.formatting.OpenFileNotificationActon
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigManager._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.{DefaultVersion, ScalafmtResolveError, ScalafmtVersion}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtNotifications.FmtVerbosity
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.exceptions.ScalafmtConfigException
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.{ScalafmtDynamicConfig, ScalafmtReflect}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.ScalaCollectionsUtil
import org.jetbrains.sbt.language.SbtFileImpl

import scala.collection.mutable
import scala.util.Try

class ScalafmtDynamicConfigManager(implicit project: Project) extends ProjectComponent {
  private val Log = Logger.getInstance(classOf[ScalafmtDynamicConfigManager])

  private val configsCache: mutable.Map[String, CachedConfig] = ScalaCollectionsUtil.newConcurrentMap

  override def projectOpened(): Unit = {
    init()
  }

  override def projectClosed(): Unit = {
    clearCaches()
  }

  def init(): Unit = {
    val scalaSettings = ScalaCodeStyleSettings.getInstance(project)
    val isScalafmtEnabled = scalaSettings.USE_SCALAFMT_FORMATTER
    if (isScalafmtEnabled) {
      val configFile = scalafmtProjectConfigFile(scalaSettings.SCALAFMT_CONFIG_PATH)
      val version = configFile.flatMap(readVersion(_).toOption.flatten).getOrElse(DefaultVersion)
      ScalafmtDynamicService.instance.resolveAsync(version, project)
    }
  }

  def clearCaches(): Unit = {
    configsCache.clear()
  }

  def resolveConfigAsync(configFile: VirtualFile,
                         version: ScalafmtVersion,
                         verbosity: FmtVerbosity,
                         onResolveFinished: ConfigResolveResult => Unit): Unit = {
    ScalafmtDynamicService.instance.resolveAsync(version, project, _ => {
      refreshFileModificationTimestamp(configFile)
      val configOrError = resolveConfig(
        configFile, Some(version),
        verbosity,
        resolveFast = true
      )
      onResolveFinished(configOrError)
    })
  }

  private def refreshFileModificationTimestamp(vFile: VirtualFile): Unit = {
    invokeAndWait(inWriteAction(vFile.refresh(false, false)))
  }

  def configForFile(psiFile: PsiFile,
                    verbosity: FmtVerbosity = FmtVerbosity.Verbose,
                    resolveFast: Boolean = false): Option[ScalafmtDynamicConfig] = {
    val settings = CodeStyle.getCustomSettings(psiFile, classOf[ScalaCodeStyleSettings])
    val configPath = settings.SCALAFMT_CONFIG_PATH

    val config = scalafmtProjectConfigFile(configPath) match {
      case Some(file) =>
        resolveConfig(file, Some(DefaultVersion), verbosity, resolveFast).toOption
      case None =>
        intellijDefaultConfig
    }
    if (psiFile.isInstanceOf[SbtFileImpl]) {
      config.map(_.withSbtDialect)
    } else {
      config
    }
  }

  private def intellijDefaultConfig: Option[ScalafmtDynamicConfig] = {
    ScalafmtDynamicService.instance.resolve(DefaultVersion, downloadIfMissing = false).toOption.map(_.intellijScalaFmtConfig)
  }

  private def resolveConfig(configFile: VirtualFile,
                            defaultVersion: Option[ScalafmtVersion],
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
          case Left(error: ConfigResolveError.ConfigScalafmtResolveError) =>
            Left(error) // do not report, rely on ScalafmtDynamicUtil resolve error reporting
          case Left(error: ConfigResolveError.ConfigError) =>
            if (verbosity == FmtVerbosity.Verbose)
              reportConfigResolveError(configFile, error)
            Left(error)
        }
    }
  }

  private def resolvingConfigWithScalafmt(configFile: VirtualFile,
                                          defaultVersion: Option[ScalafmtVersion],
                                          verbosity: FmtVerbosity,
                                          resolveFast: Boolean): ConfigResolveResult = {
    val configPath = configFile.getPath
    for {
      _ <- Option(configFile).filter(_.exists).toRight {
        ConfigResolveError.ConfigFileNotFound(configPath)
      }
      version <- readVersion(configFile) match {
        case Right(Some(value)) =>
          Right(value)
        case Right(None) =>
          defaultVersion.toRight(ConfigResolveError.ConfigMissingVersion(configPath))
        case Left(e) =>
          Left(ConfigResolveError.ConfigParseError(configPath, e))
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
      val versionChanged = cachedConfig.nonEmpty && !cachedConfig.map(_.config.version).contains(config.version)
      val versionChangeDetails = if (versionChanged) s" (v${config.version})" else ""
      ScalafmtNotifications.displayInfo(s"Scalafmt picked up new style configuration$versionChangeDetails")
    }
  }

  private def reportConfigResolveError(configFile: VirtualFile, configError: ConfigResolveError.ConfigError): Unit = {
    import ConfigResolveError._
    val details = configError match {
      case ConfigFileNotFound(_) => s"file not found"
      case ConfigMissingVersion(_) => "missing version setting"
      case ConfigParseError(_, cause) => s"parse error: ${cause.getMessage}"
      case UnknownError(unknownMessage, _) => s"unknown error: $unknownMessage"
    }
    val errorMessage = s"Failed to load scalafmt config:<br>$details"
    // NOTE: ConfError does not provide any information about parsing error position, so setting offset to zero
    val openFileAction = new OpenFileNotificationActon(project, configFile, offset = 0, title = "open config file")
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

  def scalafmtProjectConfigFile(configPath: String): Option[VirtualFile] =
    if (configPath.isEmpty) {
      defaultConfigurationFile
    } else {
      absolutePathFromConfigPath(configPath)
        .flatMap(path => StandardFileSystems.local.findFileByPath(path).toOption)
    }

  private def defaultConfigurationFile: Option[VirtualFile] =
    project.getBaseDir.toOption
      .flatMap(_.findChild(DefaultConfigurationFileName).toOption)

  def absolutePathFromConfigPath(path: String): Option[String] = {
    project.getBaseDir.toOption.map { baseDir =>
      if (path.startsWith(".")) baseDir.getCanonicalPath + "/" + path
      else path
    }
  }
}

object ScalafmtDynamicConfigManager {
  val DefaultConfigurationFileName: String = ".scalafmt.conf"

  def instanceIn(project: Project): ScalafmtDynamicConfigManager =
    project.getComponent(classOf[ScalafmtDynamicConfigManager])

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
    case class ConfigMissingVersion(configPath: String) extends ConfigError
    case class ConfigParseError(configPath: String, cause: Throwable) extends ConfigError
    case class ConfigScalafmtResolveError(error: ScalafmtResolveError) extends ConfigResolveError
    case class UnknownError(message: String, cause: Option[Throwable]) extends ConfigError

    object UnknownError {
      def apply(cause: Throwable): UnknownError = new UnknownError(cause.getMessage, Option(cause))
    }
  }

  private case class CachedConfig(config: ScalafmtDynamicConfig,
                                  vFileModificationTimestamp: Long,
                                  docModificationTimestamp: Long)
}
