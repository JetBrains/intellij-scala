package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import java.io.File
import java.nio.charset.Charset

import com.intellij.application.options.CodeStyle
import com.intellij.notification.{Notification, NotificationAction}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileEditorManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.PsiFile
import org.apache.commons.io.FileUtils
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{inWriteAction, _}
import org.jetbrains.plugins.scala.lang.formatting.OpenFileNotificationActon
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigServiceImpl._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.{DefaultVersion, ScalafmtVersion}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtNotifications.FmtVerbosity
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.exceptions.ScalafmtConfigException
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.{ScalafmtReflect, ScalafmtReflectConfig}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.utils.ScalafmtConfigUtils
import org.jetbrains.plugins.scala.lang.formatting.settings.{ScalaCodeStyleSettings, ScalaFmtSettingsPanel}
import org.jetbrains.plugins.scala.settings.ShowSettingsUtilImplExt
import org.jetbrains.plugins.scala.util.ScalaCollectionsUtil
import org.jetbrains.sbt.language.SbtFileImpl

import scala.collection.mutable
import scala.util.Try

final class ScalafmtDynamicConfigServiceImpl(private val project: Project)
  extends ScalafmtDynamicConfigService {

  private val Log = Logger.getInstance(classOf[ScalafmtDynamicConfigService])

  private val configsCache: mutable.Map[String, CachedConfig] = ScalaCollectionsUtil.newConcurrentMap

  override def init(): Unit = {
    val scalaSettings = ScalaCodeStyleSettings.getInstance(project)
    val isScalafmtEnabled = scalaSettings.USE_SCALAFMT_FORMATTER
    if (isScalafmtEnabled) {
      val configFile = ScalafmtConfigUtils.projectConfigFile(project, scalaSettings.SCALAFMT_CONFIG_PATH)
      val version = configFile.flatMap(readVersion(_).toOption.flatten).getOrElse(DefaultVersion)
      ScalafmtDynamicService.instance.resolveAsync(version, project)
    }
  }

  override def clearCaches(): Unit =
    configsCache.clear()

  override def resolveConfigAsync(
    configFile: VirtualFile,
    version: ScalafmtVersion,
    verbosity: FmtVerbosity,
    onResolveFinished: ConfigResolveResult => Unit
  ): Unit =
    ScalafmtDynamicService.instance.resolveAsync(version, project, _ => {
      refreshFileModificationTimestamp(configFile)
      val configOrError = resolveConfig(configFile, version, verbosity, resolveFast = true)
      onResolveFinished(configOrError)
    })

  private def refreshFileModificationTimestamp(vFile: VirtualFile): Unit =
    invokeAndWait(inWriteAction(vFile.refresh(false, false)))

  override def configForFileWithTimestamp(
    psiFile: PsiFile,
    verbosity: FmtVerbosity,
    resolveFast: Boolean
  ): Option[(ScalafmtReflectConfig, Option[Long])] = {
    val settings = CodeStyle.getCustomSettings(psiFile, classOf[ScalaCodeStyleSettings])
    val configPath = settings.SCALAFMT_CONFIG_PATH

    val actualConfigPath = ScalafmtConfigUtils.actualConfigPath(configPath)

    val configFile = ScalafmtConfigUtils.projectConfigFile(project, actualConfigPath)
    val config = configFile match {
      case Some(file) =>
        resolveConfig(file, DefaultVersion, verbosity, resolveFast).toOption
      case _ if settings.SCALAFMT_FALLBACK_TO_DEFAULT_SETTINGS =>
        intellijDefaultConfig
      case _ =>
        reportConfigurationFileNotFound(actualConfigPath)
        None
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

  private def intellijDefaultConfig: Option[ScalafmtReflectConfig] = {
    ScalafmtDynamicService.instance
      .resolve(DefaultVersion, downloadIfMissing = false, FmtVerbosity.FailSilent)
      .toOption.map(_.intellijScalaFmtConfig)
  }

  private def reportConfigurationFileNotFound(configPath: String): Unit = {
    val actionConfigure = new NotificationAction(ScalaBundle.message("scalafmt.can.not.find.config.file.go.to.settings")) {
      override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
        notification.expire()
        val filter = ScalaFmtSettingsPanel.SearchFilter.ScalafmtConfiguration
        ShowSettingsUtilImplExt.showScalaCodeStyleSettingsDialog(project, filter)
      }
    }
    val actionCreate = new NotificationAction(ScalaBundle.message("scalafmt.can.not.find.config.file.create.new")) {
      override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
        notification.expire()

        val result = createConfigurationFile(project, DefaultConfigurationFileName, openInEditor = true)
        result match {
          case Left(ex)  =>
            ex.foreach(Log.error("Couldn't create scalafmt configuration file", _))
            val message = ScalaBundle.message("scalafmt.can.not.create.config.file") + ex.fold("")(":<br>" + _.getMessage)
            ScalafmtNotifications.displayError(message)
          case Right(_) =>
        }
      }
    }

    ScalafmtNotifications.displayError(
      ScalaBundle.message("scalafmt.can.not.find.config.file", configPath),
      Seq(actionCreate, actionConfigure)
    )
  }

  private def resolveConfig(
    configFile: VirtualFile,
    defaultVersion: ScalafmtVersion,
    verbosity: FmtVerbosity,
    resolveFast: Boolean
  ): ConfigResolveResult = {
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

  private def notifyConfigChanges(config: ScalafmtReflectConfig, cachedConfig: Option[CachedConfig]): Unit = {
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
      /** reported in [[ScalafmtDynamicServiceImpl.reportResolveError]] */
      case _: ConfigScalafmtResolveError   => return
    }
    val errorMessage = ScalaBundle.message("scalafmt.config.load.errors.failed.to.load.config") + ":<br>" + details
    // NOTE: ConfError does not provide any information about parsing error position, so setting offset to zero
    val openFileAction = new OpenFileNotificationActon(project, configFile, offset = 0, title = ScalaBundle.message("scalafmt.config.load.actions.open.config.file"))
    ScalafmtNotifications.displayWarning(errorMessage, Seq(openFileAction))
  }

  override def isFileIncludedInProject(file: PsiFile, config: ScalafmtReflectConfig): Boolean =
    file.getVirtualFile.toOption match {
      case Some(vFile) => config.isIncludedInProject(vFile.getPath)
      case None => true // in-memory files can't be in scalafmt config exclude rules, they should always be formatted
    }

  override def isFileIncludedInProject(file: PsiFile): Boolean = {
    val configOpt = configForFile(file)
    configOpt.exists(isFileIncludedInProject(file, _))
  }
}

object ScalafmtDynamicConfigServiceImpl {

  private case class CachedConfig(config: ScalafmtReflectConfig,
                                  vFileModificationTimestamp: Long,
                                  docModificationTimestamp: Long)

  def createConfigurationFile(project: Project, path: String, openInEditor: Boolean): Either[Option[Throwable], VirtualFile] =
    ScalafmtConfigUtils.projectConfigFileAbsolutePath(project, path) match {
      case Some(path) =>
        val tri = Try {
          val file = new File(path)
          FileUtils.writeStringToFile(file, "version = 2.5.0", Charset.forName("UTF-8"))
          val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
          if (openInEditor)
            invokeLater {
              FileEditorManager.getInstance(project).openFile(vFile, true)
            }
          vFile
        }
        tri.toEither.left.map(Some(_))
      case _ =>
        Left(None)
    }

}
