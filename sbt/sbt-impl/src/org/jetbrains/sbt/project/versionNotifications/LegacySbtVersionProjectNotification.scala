package org.jetbrains.sbt.project.versionNotifications

import com.intellij.ide.BrowserUtil
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbService.DumbModeListener
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.{Nls, TestOnly, VisibleForTesting}
import org.jetbrains.plugins.scala.extensions.executeOnPooledThread
import org.jetbrains.plugins.scala.startup.ProjectActivity
import org.jetbrains.sbt.{SbtBundle, SbtUtil, SbtVersion, SbtVersionDetector}

import java.nio.file.Path

/**
 * Detects projects that still use sbt 0.13 after project opening and shows a project-level
 * IDE notification pointing users to migration resources, with an action to open
 * `project/build.properties`.
 *
 * The Build Tool Window counterpart used during sbt project import lives in
 * [[LegacySbtVersionBuildToolWindowWarning]].
 *
 * @see [[org.jetbrains.plugins.scala.util.ScalaNotificationGroups]]
 * @see SCL-23606
 */
private object LegacySbtVersionProjectNotification {

  private def LegacySbtVersionGroup: NotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("sbt.legacy.version.detected")

  // For IDEA-based projects
  final class MyProjectActivity extends ProjectActivity {
    override def execute(project: Project): Unit = {
      onProjectLoaded(project)
    }
  }

  // For external system (sbt) projects: when sbt imports a project, MyProjectActivity runs before the
  // external system has populated the project root / sbt model, so SbtVersionDetector can't yet read
  // `project/build.properties`. The dumb-mode-exit fires after that import finishes, giving the detector
  // a second chance when the data is actually available.
  class MyDumbModeListener extends DumbModeListener {
    override def exitDumbMode(): Unit = {
      executeOnPooledThread {
        checkAllOpenProjects()
      }
    }
  }

  private def checkAllOpenProjects(): Unit = {
    // Note, `isDisposed` must be explicitly checked
    val openNonDisposedProjects = ProjectManager.getInstance().getOpenProjects.filterNot(_.isDisposed)
    openNonDisposedProjects.foreach(onProjectLoaded)
  }

  @TestOnly
  def onProjectLoadedFromUnknownSource(project: Project): Unit = {
    onProjectLoaded(project)
  }

  @RequiresBackgroundThread
  private def onProjectLoaded(project: Project): Unit = {
    if (project.isDisposed)
      return

    if (isShownInCurrentSession(project))
      return

    val projectRootOpt = SbtUtil.getWorkingDirPathOpt(project).orElse(Option(project.getBasePath))
    for {
      projectRoot <- projectRootOpt
      sbtVersion <- SbtVersionDetector.detectSbtVersionFromProjectProperties(Path.of(projectRoot))
      if sbtVersion.isSbt0
    } {
      WarningShownLock.synchronized {
        if (!isShownInCurrentSession(project)) {
          showLegacySbtVersionWarning(project, Path.of(projectRoot), sbtVersion)
          setShownInCurrentSession(project)
        }
      }
    }
  }

  // NOTE: the notification will be shown every time the project is opened.
  // This might be annoying, but sbt 0.13 is too old to be tolerated.
  // As a last resort, users will still be able to mute/disable the notification via a standard IntelliJ mechanism
  private val WarningShownKey = Key.create[java.lang.Boolean]("sbt.legacy.version.warning.shown")

  // This notification is checked only on project startup / dumb-mode exit, so a single coarse lock is enough.
  // It keeps MyProjectActivity and MyDumbModeListener from showing duplicate notifications for the same project.
  // This behavior could be observed mostly in tests
  private val WarningShownLock = new Object

  private def isShownInCurrentSession(project: Project): Boolean =
    project.getUserData(WarningShownKey) != null

  private def setShownInCurrentSession(project: Project): Unit = {
    project.putUserData(WarningShownKey, java.lang.Boolean.TRUE)
  }

  @VisibleForTesting
  private[versionNotifications] def clearShownInCurrentSession(project: Project): Unit = {
    project.putUserData(WarningShownKey, null)
  }

  private def showLegacySbtVersionWarning(project: Project, projectRoot: Path, sbtVersion: SbtVersion): Unit = {
    @Nls val title = SbtBundle.message("sbt.legacy.version.project.notification.title", sbtVersion)
    @Nls val content = SbtBundle.message("sbt.legacy.version.detected.details")

    val actions = Seq(
      new OpenMigrationGuideAction,
      new OpenBuildPropertiesAction(project, projectRoot),
    )

    val notification = LegacySbtVersionGroup.createNotification(title, content, NotificationType.WARNING)
    actions.foreach(notification.addAction)
    notification.notify(project)
  }

  private final class OpenMigrationGuideAction extends NotificationAction(
    SbtBundle.message("sbt.legacy.version.project.notification.actions.open.migration.guide")
  ) {
    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
      BrowserUtil.browse(LegacySbtVersionUtils.MigrationGuideUrl)
    }
  }

  private final class OpenBuildPropertiesAction(project: Project, projectRoot: Path) extends NotificationAction(
    SbtBundle.message("sbt.legacy.version.project.notification.actions.open.properties.file")
  ) {
    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
      LegacySbtVersionUtils.createBuildPropertiesOpenFileDescriptor(project, projectRoot).foreach(_.navigate(true))
    }
  }
}
