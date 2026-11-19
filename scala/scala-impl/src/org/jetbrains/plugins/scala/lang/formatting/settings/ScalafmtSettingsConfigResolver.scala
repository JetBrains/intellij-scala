package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.openapi.editor.Document
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.util.concurrency.annotations.{RequiresBackgroundThread, RequiresEdt}
import com.typesafe.config.Config
import org.jetbrains.plugins.scala.settings.ScalaSettingsBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, inReadAction}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService.ConfigResolveError
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService.ConfigResolveError.{ConfigFileNotFound, ConfigParseError, ConfigScalafmtResolveError}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.DefaultVersion
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicServiceImpl.ProgressIndicatorDownloadListener
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtNotifications.FmtVerbosity
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.utils.ScalafmtConfigUtils
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.{ScalafmtDynamicConfigServiceImpl, ScalafmtDynamicService, projectResolvers}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalafmtSettingsConfigResolver.{Completion, LoadedConfig}
import org.scalafmt.dynamic.ScalafmtVersion

import java.nio.file.FileAlreadyExistsException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loads and resolves Settings configurations without updating UI.
 * Callers own completion dispatch, request ordering and disposal checks.
 */
private[settings] trait ScalafmtSettingsConfigResolver {
  /**
   * Starts one cancellable background task for file lookup, document loading, HOCON parsing,
   * formatter resolution and validation. No file contents are read on the calling thread.
   *
   * `onLoaded` delivers the preview on the worker before parsing or downloading the formatter.
   * `onFinished` delivers exactly one terminal outcome: resolution on the worker, or cancellation
   * or an unexpected failure on EDT through the task lifecycle callbacks. Cancellation is
   * cooperative and may delay completion. Callers must dispatch UI work and reject obsolete results.
   *
   * @param project project supplying dependency resolvers; may be the default project
   * @param configPath requested path, with an empty path selecting the project's default file
   * @param onLoaded loaded source document and preview baseline, or None for a missing file
   * @param onFinished terminal resolution, cancellation or unexpected failure
   * @param createConfig whether to create the requested configuration before loading it
   * @return progress indicator whose cancellation requests that this task stop
   */
  @RequiresEdt
  def resolveConfigAsync(
    project: Project,
    configPath: String,
    onLoaded: Option[LoadedConfig] => Unit,
    onFinished: Completion => Unit,
    createConfig: Boolean = false
  ): ProgressIndicator
}

/**
 * Runs the Settings pipeline using synchronous service primitives inside one background task.
 */
private[settings] object ScalafmtSettingsConfigResolver extends ScalafmtSettingsConfigResolver {
  /**
   * A loaded source document and the text initially shown in the separate preview editor.
   * Keeping the document alive lets Apply save preview edits without loading a file on EDT.
   */
  final case class LoadedConfig(file: VirtualFile, document: Document, originalText: String)

  /**
   * A formatter version together with whether the configuration selected the default version.
   */
  final case class Version(value: ScalafmtVersion, isDefault: Boolean)

  /**
   * A failure retaining the formatter version selected before it occurred, when known.
   */
  final case class Failure(error: ConfigResolveError, requestedVersion: Option[Version])

  /**
   * Formatter metadata on success, or a failure retaining any known requested version.
   */
  type Result = Either[Failure, Version]

  /**
   * A terminal outcome, including interruption before a preview could be loaded.
   */
  sealed trait Completion

  /**
   * Distinguishes normal resolution (including configuration errors) from interrupted work.
   */
  object Completion {
    /**
     * The pipeline resolved the formatter or found a configuration or formatter error.
     */
    final case class Resolved(result: Result) extends Completion

    /**
     * Cancellation stopped the pipeline; no missing-file conclusion can be drawn from it.
     */
    case object Cancelled extends Completion

    /**
     * An unexpected exception stopped loading or resolution.
     */
    final case class Failed(cause: Throwable) extends Completion
  }

  private val Log = Logger.getInstance(getClass)

  /**
   * Schedules file loading and resolution, returning without waiting for either stage.
   */
  @RequiresEdt
  override def resolveConfigAsync(
    project: Project,
    configPath: String,
    onLoaded: Option[LoadedConfig] => Unit,
    onFinished: Completion => Unit,
    createConfig: Boolean
  ): ProgressIndicator = {
    val task = new ResolveConfigurationTask(project, configPath, onLoaded, onFinished, createConfig)
    val indicator = new BackgroundableProcessIndicator(task)
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator)
    indicator
  }

  /**
   * Executes one [[resolveConfigAsync]] request and owns its at-most-once terminal completion.
   * File loading, preview delivery and formatter resolution run in order on the same worker;
   * task lifecycle callbacks can also complete the request after cancellation or failure.
   */
  private final class ResolveConfigurationTask(
    project: Project,
    configPath: String,
    onLoaded: Option[LoadedConfig] => Unit,
    onFinished: Completion => Unit,
    createConfig: Boolean
  ) extends Task.Backgroundable(project, ScalaSettingsBundle.message("scalafmt.settings.panel.loading.configuration"), true) {
    private val finished = new AtomicBoolean(false)

    override def onCancel(): Unit = complete(Completion.Cancelled)

    override def onThrowable(error: Throwable): Unit = {
      Log.warn("Could not load or resolve the Settings Scalafmt configuration", error)
      complete(Completion.Failed(error))
    }

    @RequiresBackgroundThread
    override def run(indicator: ProgressIndicator): Unit = {
      indicator.setIndeterminate(true)
      indicator.checkCanceled()

      findOrCreateConfigurationFile() match {
        case Left(failure) =>
          onLoaded(None)
          indicator.checkCanceled()
          complete(Completion.Resolved(Left(failure)))
        case Right(file) =>
          val loaded = loadConfiguration(file)
          indicator.checkCanceled()
          onLoaded(loaded)

          val result = resolveLoadedConfig(project, loaded, indicator)
          indicator.checkCanceled()
          complete(Completion.Resolved(result))
      }
    }

    @RequiresBackgroundThread
    private def findOrCreateConfigurationFile(): Either[Failure, Option[VirtualFile]] = {
      val actualPath = ScalafmtConfigUtils.actualConfigPath(configPath)
      if (createConfig) {
        ScalafmtDynamicConfigServiceImpl.createConfigurationFile(
          project, actualPath, openInEditor = true
        ) match {
          case Right(created) =>
            Right(Some(created))
          case Left(Some(_: FileAlreadyExistsException)) =>
            // Creation can report an existing file if it appeared after the missing-file check.
            // See ScalafmtDynamicConfigServiceImpl.createConfigurationFile for the creation contract.
            Right(refreshConfigurationFile(project, actualPath))
          case Left(error) =>
            val failure = error.fold[ConfigResolveError](ConfigFileNotFound(configPath))(ConfigParseError(configPath, _))
            Left(Failure(failure, None))
        }
      } else {
        Right(refreshConfigurationFile(project, actualPath))
      }
    }

    @RequiresBackgroundThread
    private def loadConfiguration(file: Option[VirtualFile]): Option[LoadedConfig] =
      file.flatMap { configFile =>
        inReadAction {
          FileDocumentManager.getInstance().getDocument(configFile).toOption.map { document =>
            LoadedConfig(configFile, document, document.getText)
          }
        }
      }

    private def complete(completion: Completion): Unit = {
      if (finished.compareAndSet(false, true)) {
        onFinished(completion)
      }
    }
  }

  @RequiresBackgroundThread
  private def refreshConfigurationFile(project: Project, configPath: String): Option[VirtualFile] = {
    val path = inReadAction(ScalafmtConfigUtils.projectConfigFileAbsolutePath(project, configPath))
    // Synchronous refresh must run outside read access: VFS events can require an EDT write action.
    path.flatMap(p => Option(LocalFileSystem.getInstance().refreshAndFindFileByPath(p)))
  }

  @RequiresBackgroundThread
  private def resolveLoadedConfig(project: Project, loaded: Option[LoadedConfig], indicator: ProgressIndicator): Result = {
    import ScalafmtDynamicConfigServiceImpl.{readConfig, readVersion}

    // Reuse the same HOCON snapshot for selecting the version and validating formatter settings.
    val config = loaded match {
      case Some(source) => readConfig(project, source.file, source.originalText).map(hocon => Some(source.file -> hocon))
      case None => Right(None)
    }

    val configuredVersion = config.flatMap {
      case Some((file, hocon)) => readVersion(hocon, file)
      case None => Right(None)
    }

    configuredVersion.left.map(Failure(_, None)).flatMap { versionOpt =>
      resolveFormatterAndValidateConfig(project, config.toOption.flatten, versionOpt, indicator)
    }
  }

  @RequiresBackgroundThread
  private def resolveFormatterAndValidateConfig(
    project: Project,
    config: Option[(VirtualFile, Config)],
    versionOpt: Option[ScalafmtVersion],
    indicator: ProgressIndicator
  ): Result = {
    import ScalafmtDynamicConfigServiceImpl.parseConfig

    val version = Version(versionOpt.getOrElse(DefaultVersion), isDefault = versionOpt.isEmpty)
    indicator.checkCanceled()

    val resolvers = inReadAction(projectResolvers(project))
    val formatter = ScalafmtDynamicService.instance.resolve(
      version.value,
      project,
      downloadIfMissing = true,
      FmtVerbosity.Silent,
      resolvers,
      progressListener = new ProgressIndicatorDownloadListener(indicator)
    )
    indicator.checkCanceled()

    val validated = formatter.left.map(ConfigScalafmtResolveError.apply).flatMap { fmt =>
      config match {
        case Some((file, hocon)) => parseConfig(hocon, file, fmt).map(_ => version)
        case None => Right(version)
      }
    }
    validated.left.map(Failure(_, Some(version)))
  }
}
