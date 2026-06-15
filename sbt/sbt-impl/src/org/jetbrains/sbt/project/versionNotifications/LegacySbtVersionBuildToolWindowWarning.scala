package org.jetbrains.sbt.project.versionNotifications

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.sbt.{SbtBundle, SbtVersion}

import java.nio.file.Path

/**
 * Reports a Build Tool Window warning during sbt project import when a legacy sbt 0.x version
 * is detected. The message points users to migration resources and, when possible, attaches an
 * "open `project/build.properties`" navigatable.
 *
 * The post-open IDE-notification counterpart lives in [[LegacySbtVersionProjectNotification]].
 *
 * @see SCL-23606
 */
private[sbt] object LegacySbtVersionBuildToolWindowWarning {

  private val LatestReleasesGithubUrl: String = "https://github.com/sbt/sbt/releases"
  private val LatestReleasesScalaSbtOrUrl: String = "https://www.scala-sbt.org/download"

  def warnForBuildToolWindowIfNeeded(
    project: Project,
    projectRoot: Path,
    sbtVersion: SbtVersion,
    buildReporter: BuildReporter
  ): Unit = {
    if (!sbtVersion.isSbt0) return

    val message = warningForBuildToolWindowMessage(sbtVersion)
    val details = warningForBuildToolWindowDetails
    val openBuildPropertiesFile = LegacySbtVersionUtils.createBuildPropertiesOpenFileDescriptor(project, projectRoot)
    buildReporter.warning(message, None, details, openBuildPropertiesFile)
  }

  @Nls
  private def warningForBuildToolWindowMessage(sbtVersion: SbtVersion): String =
    SbtBundle.message("sbt.legacy.version.detected.in.build.tool.window", sbtVersion.minor)

  @Nls
  private def warningForBuildToolWindowDetails: String = {
    val details = s"""${SbtBundle.message("sbt.legacy.version.detected.details")}"""
    val helpfulResources = SbtBundle.message(
      "sbt.legacy.version.detected.details.helpful.resources",
      LegacySbtVersionUtils.MigrationGuideUrl,
      LatestReleasesGithubUrl,
      LatestReleasesScalaSbtOrUrl
    )
    //noinspection ScalaExtractStringToBundle
    s"$details\n\n$helpfulResources"
  }
}
