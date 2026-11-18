package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService.ConfigResolveError
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService.ConfigResolveError.ConfigScalafmtResolveError
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.DefaultVersion
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtNotifications.FmtVerbosity
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.{ScalafmtDynamicConfigService, ScalafmtDynamicConfigServiceImpl, ScalafmtDynamicService}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalafmtSettingsConfigResolver.Result
import org.scalafmt.dynamic.ScalafmtVersion

/**
 * Resolves formatter configurations for Settings without updating UI.
 *
 * Callers own completion dispatch, request ordering and disposal checks. Implementations
 * do not guarantee that completion runs on a particular thread.
 */
private[settings] trait ScalafmtSettingsConfigResolver {
  /**
   * Resolves the configuration, downloading its formatter asynchronously when necessary.
   *
   * Initial configuration reading and HOCON parsing run on the calling thread. Completion may
   * happen before this method returns or later, on the calling thread, EDT or a background thread.
   * Callers must dispatch UI work themselves; `Async` does not guarantee background execution.
   *
   * @param project project whose configuration service and dependency resolvers are used;
   *                may be the default project
   * @param configFile configuration file from which to read the formatter version
   * @param onResolved completion callback receiving formatter metadata or a resolution failure
   */
  def resolveConfigAsync(project: Project, configFile: VirtualFile, onResolved: Result => Unit): Unit

  /**
   * Requests asynchronous resolution of the default formatter without loading a configuration file.
   *
   * Returns without waiting for resolution and does not expose a completion callback.
   *
   * @param project project associated with any resolution progress or error notifications
   */
  def resolveDefaultFormatterAsync(project: Project): Unit
}

/**
 * Adapts the project configuration service and application formatter service to the Settings resolver.
 * Default projects have no configuration service, so only their requested formatter is resolved.
 */
private[settings] object ScalafmtSettingsConfigResolver extends ScalafmtSettingsConfigResolver {
  /**
   * A formatter version together with its origin in the configuration.
   *
   * @param value selected or resolved formatter version
   * @param isDefault whether the configuration omitted its version and selected the default
   */
  final case class Version(value: ScalafmtVersion, isDefault: Boolean)

  /**
   * A resolution failure with the formatter version selected before it occurred, when known.
   *
   * Reading the version can fail before a formatter is selected. Later failures retain the
   * requested version so the panel can display it alongside the error.
   *
   * @param error configuration parsing or formatter resolution failure
   * @param requestedVersion selected formatter metadata, or None if version reading failed
   */
  final case class Failure(error: ConfigResolveError, requestedVersion: Option[Version])

  /**
   * Formatter metadata on success, or a failure retaining any known requested version.
   */
  type Result = Either[Failure, Version]

  private val Log = Logger.getInstance(getClass)

  /**
   * Reads the version on the calling thread, reporting parsing failures before starting asynchronous resolution.
   */
  override def resolveConfigAsync(project: Project, configFile: VirtualFile, onResolved: Result => Unit): Unit = {
    val versionOpt = ScalafmtDynamicConfigServiceImpl.readVersion(project, configFile) match {
      case Right(version) => version
      case Left(error) =>
        onResolved(Left(Failure(error, None)))
        return
    }
    resolveConfigWithVersion(project, configFile, versionOpt, onResolved)
  }

  private def resolveConfigWithVersion(
    project: Project,
    configFile: VirtualFile,
    configuredVersion: Option[ScalafmtVersion],
    onResolved: Result => Unit
  ): Unit = {
    val requestedVersion = Version(configuredVersion.getOrElse(DefaultVersion), isDefault = configuredVersion.isEmpty)

    def completed(result: Either[ConfigResolveError, ScalafmtVersion]): Unit = {
      val mappedResult: Result = result.fold(
        error => {
          Left(Failure(error, Some(requestedVersion)))
        },
        version => {
          Right(Version(version, requestedVersion.isDefault))
        }
      )
      onResolved(mappedResult)
    }

    ScalafmtDynamicConfigService.instanceIn(project).toOption match {
      case Some(configManager) =>
        configManager.resolveConfigAsync(
          configFile,
          requestedVersion.value,
          FmtVerbosity.Silent,
          result => {
            completed(result.map(_.getVersion))
          }
        )
      case None =>
        Log.assertTrue(project.isDefault, "Config manager is expected to be missing only in default projects")
        ScalafmtDynamicService.instance.resolveAsync(
          requestedVersion.value,
          project,
          result => {
            completed(result.map(_.version).left.map(ConfigScalafmtResolveError.apply))
          }
        )
    }
  }

  /**
   * Starts default formatter resolution through the application service without waiting for completion.
   */
  override def resolveDefaultFormatterAsync(project: Project): Unit =
    ScalafmtDynamicService.instance.resolveAsync(DefaultVersion, project)
}
