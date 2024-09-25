package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.application.options.CodeStyle
import com.intellij.notification.{Notification, NotificationAction}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileEditorManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.typesafe.config._
import org.apache.commons.io.FileUtils
import org.apache.commons.text.StringEscapeUtils
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.OpenFileNotificationActon
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService.ConfigResolveError.ConfigCyclicDependencyException
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigServiceImpl._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.DefaultVersion
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtNotifications.FmtVerbosity
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.utils.ScalafmtConfigUtils
import org.jetbrains.plugins.scala.lang.formatting.settings.{ScalaCodeStyleSettings, ScalaFmtSettingsPanel}
import org.jetbrains.plugins.scala.settings.ShowSettingsUtilImplExt
import org.jetbrains.sbt.language.SbtFile
import org.scalafmt.dynamic.{ScalafmtReflect, ScalafmtReflectConfig, ScalafmtVersion}

import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import scala.collection.{concurrent, mutable}
import scala.jdk.CollectionConverters._
import scala.util.Try

final class ScalafmtDynamicConfigServiceImpl(private implicit val project: Project)
  extends ScalafmtDynamicConfigService {

  private val Log = Logger.getInstance(classOf[ScalafmtDynamicConfigService])

  // config path -> cached config
  private val configsCache: concurrent.Map[String, CachedConfig] =
    new ConcurrentHashMap[String, CachedConfig]().asScala

  override def init(): Unit = {
    val scalaSettings = ScalaCodeStyleSettings.getInstance(project)
    val isScalafmtEnabled = scalaSettings.USE_SCALAFMT_FORMATTER
    if (isScalafmtEnabled) {
      val configFile = ScalafmtConfigUtils.projectConfigFile(project, scalaSettings.SCALAFMT_CONFIG_PATH)
      val version = configFile.flatMap(readVersion(project, _).toOption.flatten).getOrElse(DefaultVersion)
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

  override def configForFile(
    psiFile: PsiFile,
    verbosity: FmtVerbosity,
    resolveFast: Boolean
  ): Option[ScalafmtReflectConfig] = {
    val (settings, actualConfigPath, configVirtualFile) = configFileForFileImpl(psiFile)

    val config = configVirtualFile match {
      case Some(vFile) =>
        resolveConfig(vFile, DefaultVersion, verbosity, resolveFast).toOption
      case _ if settings.SCALAFMT_FALLBACK_TO_DEFAULT_SETTINGS =>
        intellijDefaultConfig
      case _ =>
        if (verbosity == FmtVerbosity.Verbose) {
          reportConfigurationFileNotFound(actualConfigPath)
        }
        None
    }
    val configWithDialect: Option[ScalafmtReflectConfig] =
      if (psiFile.is[SbtFile])
        config.map(x => x.withSbtDialect.getOrElse(x))
      else
        config

    configWithDialect
  }

  override def configFileForFile(
    psiFile: PsiFile
  ): Option[VirtualFile] =
    configFileForFileImpl(psiFile)._3

  private def configFileForFileImpl(
    psiFile: PsiFile
  ): (ScalaCodeStyleSettings, String, Option[VirtualFile]) = {
    val settings = CodeStyle.getCustomSettings(psiFile, classOf[ScalaCodeStyleSettings])
    val configPath = settings.SCALAFMT_CONFIG_PATH
    val actualConfigPath = ScalafmtConfigUtils.actualConfigPath(configPath)
    val configFile = ScalafmtConfigUtils.projectConfigFile(project, actualConfigPath)
    (settings, actualConfigPath, configFile)
  }

  private def intellijDefaultConfig: Option[ScalafmtReflectConfig] = {
    ScalafmtDynamicService.instance
      .resolve(DefaultVersion, project, downloadIfMissing = false, FmtVerbosity.FailSilent, projectResolvers(project))
      .toOption.flatMap(_.intellijScalaFmtConfig)
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

    val message = ScalaBundle.message("scalafmt.can.not.find.config.file", configPath)
    Log.warnWithErrorInTests(message)
    ScalafmtNotifications.displayError(
      message,
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

    val cachedConfig = configsCache.get(configPath)
    cachedConfig match {
      case Some(cached) if cached.isUpToDate =>
        Right(cached.config)
      case _ =>
        resolvingConfigWithScalafmt(configFile, defaultVersion, verbosity, resolveFast) match {
          case Right((config, includedFiles)) =>
            if (verbosity != FmtVerbosity.Silent) {
              notifyConfigChanges(config, cachedConfig)
            }
            val allConfigFiles = configFile +: includedFiles
            val filesModificationStamps = inReadAction  {
              allConfigFiles.map(f => f -> getModificationStamps(f)).toMap
            }
            configsCache(configPath) = CachedConfig(config, filesModificationStamps)
            Right(config)
          case Left(error) =>
            if (verbosity == FmtVerbosity.Verbose) {
              reportConfigResolveError(configFile, error)
            }
            Left(error)
        }
    }
  }

  @RequiresReadLock
  private def getModificationStamps(file: VirtualFile): ModificationStamps = {
    val document = FileDocumentManager.getInstance.getDocument(file)
    ModificationStamps(
      file.getModificationStamp,
      Option(document).map(_.getModificationStamp).getOrElse(-1)
    )
  }

  private def resolvingConfigWithScalafmt(configFile: VirtualFile,
                                          defaultVersion: ScalafmtVersion,
                                          verbosity: FmtVerbosity,
                                          resolveFast: Boolean): Either[ConfigResolveError, (ScalafmtReflectConfig, Seq[VirtualFile])] = {
    val configPath = configFile.getPath
    for {
      _ <- Option(configFile).filter(_.exists).toRight {
        ConfigResolveError.ConfigFileNotFound(configPath)
      }
      hoconConfig <- parseHoconFile(project, configFile)
      version <- readVersion(hoconConfig.config, configFile).map(_.getOrElse(defaultVersion))
      fmtReflect <- ScalafmtDynamicService.instance
        .resolve(version, project, downloadIfMissing = false, verbosity, projectResolvers(project), resolveFast)
        .left.map(ConfigResolveError.ConfigScalafmtResolveError)
      config <- parseConfig(hoconConfig.config, configFile, fmtReflect)
    } yield (config, hoconConfig.includedFiles)
  }

  private def parseConfig(config: Config, configFile: VirtualFile, fmtReflect: ScalafmtReflect): ConfigResolveResult =
    fmtReflect.parseConfigFromString(config.root.render()).toEither.left.map { x =>
      ConfigResolveError.ConfigParseError(configFile.getPath, x)
    }

  private def notifyConfigChanges(config: ScalafmtReflectConfig, cachedConfig: Option[CachedConfig]): Unit = {
    val contentChanged = !cachedConfig.map(_.config).contains(config)
    if (contentChanged) {
      ScalafmtNotifications.displayInfo(ScalaBundle.message("scalafmt.picked.new.config", config.getVersion))
    }
  }

  private def reportConfigResolveError(configFile: VirtualFile, configError: ConfigResolveError): Unit = {
    import ConfigResolveError._

    val details = configError match {
      case ConfigFileNotFound(path) =>
        Log.warnWithErrorInTests(s"config resolve error: config not found: $path")
        ScalaBundle.message("scalafmt.can.not.find.config.file", path)
      case ConfigParseError(path, cause) =>
        Log.warnWithErrorInTests(s"config resolve error: parse error: $path", cause)
        ScalaBundle.message("scalafmt.config.load.errors.parse.error", cause.getMessage)
      /** reported in [[ScalafmtDynamicServiceImpl.reportResolveError]] */
      case _: ConfigScalafmtResolveError => return
      case ConfigCyclicDependenciesError(configPath, ex) =>
        val stackReverse = ex.filesResolveStack.reverse
        val filesStackText = stackReverse.mkString("  ", "\n  ", "")
        Log.warnWithErrorInTests(s"config resolve error: cyclic includes detected during scalafmt config parse: $configPath. Files include stack:\n$filesStackText")

        val filesStackShortNotificationText = stackReverse.map(_.getName).mkString("<br>  ", "<br>  ", "")
        ScalaBundle.message("scalafmt.config.load.errors.parse.error", ScalaBundle.message("scalafmt.config.load.errors.cyclic.includes.detected")) + filesStackShortNotificationText
    }

    val errorMessage = ScalaBundle.message("scalafmt.config.load.errors.failed.to.load.config") + ":<br>" + StringEscapeUtils.escapeHtml4(details)
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

  /**
   * @param virtualFileStamp modification stamp of the virtual file
   *                         (see [[com.intellij.openapi.vfs.VirtualFile#getModificationStamp()]]
   * @param documentStamp    modification stamp of the document
   *                         (see [[com.intellij.openapi.editor.Document#getModificationStamp()]]
   * @note We need to hold both modification stamps because there can be 2 kinds of modifications to the configuration:
   *       1. External modifications of the file (e.g., from VCS).
   *          The document might still have the old text and not modified.
   *       1. Document modifications (e.g., when typing in the editor).
   *          If the document contents are not flushed to the disc, the file is not considered modified.
   */
  private case class ModificationStamps(
    virtualFileStamp: Long,
    documentStamp: Long
  )

  /**
   * @param filesModificationStamps map from files defining the configuration to their modification stamps.
   *                                    It's a collection because configuration can include
   *                                    other configurations using `include other.conf` syntax.
   */
  private case class CachedConfig(
    config: ScalafmtReflectConfig,
    filesModificationStamps: Map[VirtualFile, ModificationStamps],
  ) {

    /**
     * @return true of configuration file and document modification stamps hasn't changed since the config was cached
     * @note the method uses read action to access file documents
     */
    def isUpToDate: Boolean = {
      val filesTimestampAreTheSame = filesModificationStamps.forall { case (file, modificationStamps) =>
        file.getModificationStamp == modificationStamps.virtualFileStamp
      }
      if (!filesTimestampAreTheSame)
        return false

      val documentsTimestampAreTheSame = inReadAction {
        filesModificationStamps.forall { case (file, modificationStamps) =>
          val document = FileDocumentManager.getInstance.getDocument(file)
          document.getModificationStamp == modificationStamps.documentStamp
        }
      }
      documentsTimestampAreTheSame
    }
  }

  def createConfigurationFile(project: Project, path: String, openInEditor: Boolean): Either[Option[Throwable], VirtualFile] =
    ScalafmtConfigUtils.projectConfigFileAbsolutePath(project, path) match {
      case Some(path) =>
        val tri = Try {
          val file = new File(path)
          // TODO: implement detecting of the latest version from the Internet
          FileUtils.writeStringToFile(file, "version = 2.7.5", Charset.forName("UTF-8"))
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

  def readVersion(
    project: Project,
    configFile: VirtualFile
  ): Either[ConfigResolveError.ConfigError, Option[ScalafmtVersion]] =
    parseHoconFile(project, configFile).map(_.config).flatMap(readVersion(_, configFile))

  private def readVersion(
    config: Config,
    configFile: VirtualFile
  ): Either[ConfigResolveError.ConfigError, Option[ScalafmtVersion]] =
    Try {
      Option(config.getString("version").trim).flatMap(ScalafmtVersion.parse)
    }.toEither.left.flatMap {
      case _: ConfigException.Missing => Right(None)
      case e => Left(ConfigResolveError.ConfigParseError(configFile.getCanonicalPath, e))
    }

  private def parseHoconFile(
    project: Project,
    configFile: VirtualFile
  ): Either[ConfigResolveError.ConfigError, ConfigWithIncludedFiles] = {
    implicit val manager: FileDocumentManager = FileDocumentManager.getInstance
    Try {
      parseHoconFileImpl(project, configFile, configFile :: Nil)
    }.toEither.left.map {
      case e: ConfigCyclicDependencyException =>
        ConfigResolveError.ConfigCyclicDependenciesError(configFile.getCanonicalPath, e)
      case e =>
        ConfigResolveError.ConfigParseError(configFile.getCanonicalPath, e)
    }.flatMap {
      case Some(x) => Right(x)
      case None => Left(ConfigResolveError.ConfigFileNotFound(configFile.getCanonicalPath))
    }
  }

  private case class ConfigWithIncludedFiles(config: Config, includedFiles: Seq[VirtualFile])

  /** @param filesResolveStack track currently resolved files to detect cyclic dependencies */
  private def parseHoconFileImpl(
    project: Project,
    configFile: VirtualFile,
    filesResolveStack: List[VirtualFile]
  )(implicit manager: FileDocumentManager): Option[ConfigWithIncludedFiles] = {
    val includer = new MyConfigIncluder(project, configFile, filesResolveStack)
    val parseOpts = ConfigParseOptions.defaults.setIncluder(includer)
    val configText = fileText(configFile)
    val config = configText.map(ConfigFactory.parseString(_, parseOpts))
    config.map { c =>
      ConfigWithIncludedFiles(c, includer.getIncludedFiles)
    }
  }

  private def fileText(file: VirtualFile)
                      (implicit manager: FileDocumentManager): Option[String] =
    inReadAction {
      manager.getDocument(file).toOption.map(_.getText())
    }

  private class MyConfigIncluder(project: Project, configFile: VirtualFile, filesResolveStack: List[VirtualFile])
                                (implicit manager: FileDocumentManager) extends ConfigIncluder {
    private var fallbackOpt: Option[ConfigIncluder] = None

    private val includedFiles: mutable.Buffer[VirtualFile] = new mutable.ArrayBuffer
    def getIncludedFiles: Seq[VirtualFile] = includedFiles.toSeq

    // NOTE: it doesn't fully satisfy the overridden method contract
    // From the comment from PR from "nadavwr":
    // "Returns a new includer that falls back to the given includer."
    override def withFallback(fallback: ConfigIncluder): ConfigIncluder = {
      fallbackOpt = Some(fallback)
      this
    }

    override def include(context: ConfigIncludeContext, includedFile: String): ConfigObject = {
      val includedVFile0 = ScalafmtConfigUtils.resolveConfigIncludeFile(configFile, includedFile)
      val includedVFile = includedVFile0.orElse(ScalafmtConfigUtils.projectConfigFile(project, includedFile))
      val includedConfig = includedVFile.flatMap { file =>
        if (filesResolveStack.contains(file))
          throw ConfigCyclicDependencyException(file :: filesResolveStack)
        parseHoconFileImpl(project, file, file :: filesResolveStack)
      }

      val includedConfigWithFallback = includedConfig
        .map(_.config.root)
        .orElse(fallbackOpt.map(_.include(context, includedFile)))

      includedFiles ++= includedVFile.toSeq
      includedFiles ++= includedConfig.toSeq.flatMap(_.includedFiles)

      includedConfigWithFallback.getOrElse(emptyConfigObj)
    }
  }

  private def emptyConfigObj: ConfigObject =
    ConfigValueFactory.fromMap(Map.empty[String, Any].asJava)
}
