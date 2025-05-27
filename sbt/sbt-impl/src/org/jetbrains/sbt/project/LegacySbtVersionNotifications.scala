package org.jetbrains.sbt.project

import com.intellij.ide.BrowserUtil
import com.intellij.notification._
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbService.DumbModeListener
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.plugins.scala.extensions.{OptionExt, RichFile, executeOnPooledThread}
import org.jetbrains.plugins.scala.startup.ProjectActivity
import org.jetbrains.sbt.{Sbt, SbtBundle, SbtUtil, SbtVersion, SbtVersionDetector}

import java.io.File
import java.nio.file.Path

/**
 * Originally created based on [[org.jetbrains.plugins.scala.components.Scala3Disclaimer]]
 *
 * See also [[org.jetbrains.plugins.scala.util.ScalaNotificationGroups]]
 */
private object LegacySbtVersionNotifications {

  private def LegacySbtVersionGroup: NotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("sbt.legacy.version.detected")

  private val MigrationGuideUrl: String = "https://www.scala-sbt.org/1.x/docs/Migrating-from-sbt-013x.html"
  private val LatestReleasesGithubUrl: String = "https://github.com/sbt/sbt/releases"
  private val LatestReleasesScalaSbtOrUrl: String = "https://www.scala-sbt.org/download"

  // For IDEA-based projects
  final class MyProjectActivity extends ProjectActivity {
    override def execute(project: Project): Unit = {
      onProjectLoaded(project)
    }
  }

  // For external system projects
  class MyDumbModeListener extends DumbModeListener {
    override def exitDumbMode(): Unit = {
      executeOnPooledThread {
        checkAllOpenProjects()
      }
    }
  }

  private def checkAllOpenProjects(): Unit = {
    ProjectManager.getInstance().getOpenProjects.foreach(onProjectLoaded)
  }

  private def onProjectLoaded(project: Project): Unit = {
    if (project.isDisposed)
      return

    if (!isShownInCurrentSession(project)) {
      for {
        projectRoot <- SbtUtil.getWorkingDirPathOpt(project)
        sbtVersion <- SbtVersionDetector.detectSbtVersionFromProjectProperties(Path.of(projectRoot))
        if sbtVersion.isSbt0
      } {
        showLegacySbtVersionWarning(project, sbtVersion)
        setShownInCurrentSession(project)
      }
    }
  }

  // NOTE: the notification will be shown every time the project is opened.
  // This might be annoying, but sbt 0.13 is too old to be tolerated.
  // As a last resort, users will still be able to mute/disable the notification via a standard IntelliJ mechanism
  private val WarningShownKey = Key.create[java.lang.Boolean]("sbt.legacy.version.warning.shown")

  private def isShownInCurrentSession(project: Project): Boolean =
    project.getUserData(WarningShownKey) != null

  private def setShownInCurrentSession(project: Project): Unit = {
    project.putUserData(WarningShownKey, java.lang.Boolean.TRUE)
  }

  private def showLegacySbtVersionWarning(project: Project, sbtVersion: SbtVersion): Unit = {
    @Nls val title = SbtBundle.message("sbt.legacy.version.project.notification.title", sbtVersion)
    @Nls val content = SbtBundle.message("sbt.legacy.version.detected.details")

    val actions = Seq(
      new NotificationAction(SbtBundle.message("sbt.legacy.version.project.notification.actions.open.migration.guide")) {
        override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
          BrowserUtil.browse(MigrationGuideUrl)
        }
      },
      new NotificationAction(SbtBundle.message("sbt.legacy.version.project.notification.actions.open.properties.file")) {
        override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
          createBuildPropertiesOpenFileDescriptor(project).foreach(_.navigate(true))
        }
      }
    )

    val notification = LegacySbtVersionGroup.createNotification(title, content, NotificationType.WARNING)
    actions.foreach(notification.addAction)
    notification.notify(project)
  }

  private def createBuildPropertiesOpenFileDescriptor(project: Project): Option[OpenFileDescriptor] = {
    val settings = SbtExternalSystemManager.executionSettingsFor(project)
    val projectBaseDir = new File(settings.realProjectPath)
    createBuildPropertiesOpenFileDescriptor(project, projectBaseDir)
  }

  private def createBuildPropertiesOpenFileDescriptor(project: Project, projectRoot: File): Option[OpenFileDescriptor] = {
    val buildPropertiesFile = projectRoot / Sbt.ProjectDirectory / Sbt.PropertiesFile
    Option(buildPropertiesFile)
      .filter(_.exists()).map(_.toPath).safeMap(VirtualFileManager.getInstance.findFileByNioPath)
      .map(new OpenFileDescriptor(project, _, 0))
  }

  def warnForBuildToolWindow(
    project: Project,
    projectRoot: File,
    sbtVersion: SbtVersion,
    buildReporter: BuildReporter
  ): Unit = {
    val message = warningForBuildToolWindowMessage(sbtVersion)
    val details = warningForBuildToolWindowDetails
    val openBuildPropertiesFile = createBuildPropertiesOpenFileDescriptor(project, projectRoot)
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
      MigrationGuideUrl,
      LatestReleasesGithubUrl,
      LatestReleasesScalaSbtOrUrl
    )
    //noinspection ScalaExtractStringToBundle
    s"$details\n\n$helpfulResources"
  }
}

