package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{StandardFileSystems, VirtualFile}
import com.intellij.psi.PsiFile
import com.typesafe.config.{ConfigException, ConfigFactory}
import org.jetbrains.plugins.scala.extensions.{inWriteAction, _}
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaFmtPreFormatProcessor.OpenFileNotificationActon
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicUtil.{DefaultVersion, ScalafmtResolveError, ScalafmtVersion}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.exceptions.ScalafmtConfigException
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.{ScalafmtDynamicConfig, ScalafmtReflect}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.ScalaCollectionsUtil
import org.jetbrains.sbt.language.SbtFileImpl

import scala.collection.mutable
import scala.util.Try

object ScalafmtDynamicConfigUtil {
  private val Log = Logger.getInstance(this.getClass)

  val DefaultConfigurationFileName: String = ".scalafmt.conf"

  private val configsCache: mutable.Map[String, CachedConfig] = ScalaCollectionsUtil.newConcurrentMap

  def resolveConfigAsync(configFile: VirtualFile,
                         version: ScalafmtVersion,
                         project: ProjectContext,
                         onResolveFinished: ConfigResolveResult => Unit): Unit = {
    val backgroundTask = new Task.Backgroundable(project, "Resolving scalafmt config", true) {
      override def run(indicator: ProgressIndicator): Unit = {
        tryRefreshFileModificationTimestamp(configFile)
        indicator.setFraction(0.0)
        val configOrError = resolveConfig(configFile, Some(version), project, downloadScalafmtIfMissing = true)
        onResolveFinished(configOrError)
        indicator.setFraction(1.0)
      }
    }
    ProgressManager.getInstance().run(backgroundTask)
  }

  private def tryRefreshFileModificationTimestamp(vFile: VirtualFile): Unit = {
    invokeAndWait(inWriteAction(vFile.refresh(false, false)))
  }

  def configForFile(psiFile: PsiFile, failSilent: Boolean = false): Option[ScalafmtDynamicConfig] = {
    val settings = CodeStyle.getCustomSettings(psiFile, classOf[ScalaCodeStyleSettings])
    val project = psiFile.getProject
    val configPath = settings.SCALAFMT_CONFIG_PATH

    val configFromFile = for {
      configFile <- scalafmtProjectConfigFile(configPath, project)
      config <- resolveConfig(configFile, Some(DefaultVersion), project, downloadScalafmtIfMissing = false, failSilent).toOption
    } yield config

    val config = configFromFile.orElse(intellijDefaultConfig)
    if (psiFile.isInstanceOf[SbtFileImpl]) {
      config.map(_.withSbtDialect)
    } else {
      config
    }
  }

  private lazy val intellijDefaultConfig: Option[ScalafmtDynamicConfig] = {
    ScalafmtDynamicUtil.resolve(DefaultVersion, downloadIfMissing = false).toOption.map(_.intellijScalaFmtConfig)
  }

  private def resolveConfig(configFile: VirtualFile,
                            defaultVersion: Option[ScalafmtVersion],
                            project: ProjectContext,
                            downloadScalafmtIfMissing: Boolean,
                            failSilent: Boolean = false): ConfigResolveResult = {
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
        resolvingConfigWithScalafmt(configFile, defaultVersion, downloadScalafmtIfMissing, failSilent) match {
          case Right(config) =>
            notifyConfigChanges(config, cachedConfig)
            configsCache(configPath) = CachedConfig(config, currentVFileTimestamp, currentDocTimestamp)
            Right(config)
          case Left(error: ConfigResolveError.ConfigScalafmtResolveError) =>
            Left(error) // do not report, rely on ScalafmtDynamicUtil resolve error reporting
          case Left(error: ConfigResolveError.ConfigError) =>
            if (!failSilent)
              reportConfigResolveError(configFile, error, project)
            Left(error)
        }
    }
  }

  private def resolvingConfigWithScalafmt(configFile: VirtualFile,
                                          defaultVersion: Option[ScalafmtVersion],
                                          downloadScalafmtIfMissing: Boolean,
                                          failSilent: Boolean = false): ConfigResolveResult = {
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
      fmtReflect <- ScalafmtDynamicUtil.resolve(version, downloadScalafmtIfMissing, failSilent).left.map { error =>
        ConfigResolveError.ConfigScalafmtResolveError(error)
      }
      config <- parseConfig(configFile, fmtReflect)
    } yield config
  }

  private def parseConfig(configFile: VirtualFile, fmtReflect: ScalafmtReflect): ConfigResolveResult = {
    val document = inReadAction(FileDocumentManager.getInstance.getDocument(configFile))
    val configText = document.getText()
    Try(fmtReflect.parseConfigFromString(configText)).toEither.left.map {
      case e: ScalafmtConfigException => ConfigResolveError.ConfigParseError(configFile.getPath, e)
      case e =>
        Log.error(e)
        ConfigResolveError.UnknownError(e)
    }
  }

  private def notifyConfigChanges(config: ScalafmtDynamicConfig, cachedConfig: Option[CachedConfig]): Unit = {
    val contentChanged = !cachedConfig.map(_.config).contains(config)
    if (contentChanged) {
      val versionChanged = !cachedConfig.map(_.config.version).contains(config.version)
      val versionChangeDetails = if (versionChanged) s"<br>(new version: ${config.version})" else ""
      ScalafmtNotifications.displayInfo(s"scalafmt picked up new style configuration$versionChangeDetails")
    }
  }

  private def reportConfigResolveError(configFile: VirtualFile,
                                       configError: ConfigResolveError.ConfigError,
                                       project: ProjectContext): Unit = {
    import ConfigResolveError._
    val details = configError match {
      case ConfigFileNotFound(_) => s"file not found"
      case ConfigMissingVersion(_) => "missing version setting"
      case ConfigParseError(_, cause) => s"parse error: ${cause.getMessage}"
      case UnknownError(unknownMessage, _) => s"unknown error occurred: $unknownMessage"
    }
    val errorMessage = s"Failed to load scalafmt config ${configFile.getPath}:<br>$details"
    // NOTE: ConfError does not provide any information about parsing error position, so setting offset to zero
    val openFileAction = new OpenFileNotificationActon(project, configFile, offset = 0, title = "open config file")
    ScalafmtNotifications.displayWarning(errorMessage, Seq(openFileAction))
  }

  def readVersion(configFile: VirtualFile): Either[Throwable, Option[String]] = {
    Try {
      val document = inReadAction(FileDocumentManager.getInstance.getDocument(configFile))
      val config = ConfigFactory.parseString(document.getText)
      Option(config.getString("version").trim)
    }.toEither.left.flatMap {
      case _: ConfigException.Missing => Right(None)
      case e =>
        Log.error(e)
        Left(e)
    }
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

  def scalafmtProjectConfigFile(configPath: String, project: Project): Option[VirtualFile] =
    if (configPath.isEmpty) {
      defaultConfigurationFile(project)
    } else {
      absolutePathFromConfigPath(configPath, project)
        .flatMap(path => StandardFileSystems.local.findFileByPath(path).toOption)
    }

  private def defaultConfigurationFile(project: Project): Option[VirtualFile] =
    project.getBaseDir.toOption
      .flatMap(_.findChild(DefaultConfigurationFileName).toOption)

  def absolutePathFromConfigPath(path: String, project: Project): Option[String] = {
    project.getBaseDir.toOption.map { baseDir =>
      if (path.startsWith(".")) baseDir.getCanonicalPath + "/" + path
      else path
    }
  }

  type ConfigResolveResult = Either[ConfigResolveError, ScalafmtDynamicConfig]

  sealed trait ConfigResolveError

  object ConfigResolveError {
    trait ConfigError extends ConfigResolveError
    case class ConfigFileNotFound(configPath: String) extends ConfigError
    case class ConfigMissingVersion(configPath: String) extends ConfigError
    case class ConfigParseError(configPath: String, cause: Throwable) extends ConfigError
    case class UnknownError(message: String, cause: Option[Throwable]) extends ConfigError
    case class ConfigScalafmtResolveError(error: ScalafmtResolveError) extends ConfigResolveError

    object UnknownError {
      def apply(cause: Throwable): UnknownError = new UnknownError(cause.getMessage, Option(cause))
    }
  }

  private case class CachedConfig(config: ScalafmtDynamicConfig,
                                  vFileModificationTimestamp: Long,
                                  docModificationTimestamp: Long)
}
