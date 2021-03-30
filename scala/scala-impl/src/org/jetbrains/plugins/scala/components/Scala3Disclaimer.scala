package org.jetbrains.plugins.scala.components

import com.intellij.notification._
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.{Project, ProjectManagerListener}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.{ScalaProjectSettings, ScalaProjectSettingsConfigurable}
import org.jetbrains.plugins.scala.util.NotificationUtil.HyperlinkListener
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaBundle}
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.{SbtProjectSettings, SbtProjectSettingsListener}

import java.io.File
import java.nio.file.Files
import java.util
import java.util.function.Consumer

object Scala3Disclaimer {
  private val DottyVersion = "scalaVersion\\s*:=\\s*\"(0\\.\\S+)\"".r
  // TODO Create a constant (for project template, compiler, etc.)
  private val SupportedDottyVersion = LatestScalaVersions.Scala_3_0.minor

  private var versionInfoShown = false

  class ProjectListener extends ProjectManagerListener {
    override def projectOpened(project: Project): Unit = {
      onProjectLoaded(project) // for IDEA-based projects
    }
  }

  // TODO Re-imports are not detected (https://jetbrains.slack.com/archives/CRB2LP50C/p1585571104004100).
  class SbtProjectListener(project: Project) extends SbtProjectSettingsListener{
    // TODO Is invoked after project opening, not after actual import.
    override def onProjectsLoaded(settings: util.Collection[SbtProjectSettings]): Unit = {
      onProjectLoaded(project) // for non-IDEA-based projects
    }
    // TODO onProjectsLoaded is not invoked after an actual import, so we use onProjectRenamed to detect first import.
    override def onProjectRenamed(oldName: String, newName: String): Unit = {
      onProjectLoaded(project)
    }
    override def onProjectsLinked(settings: util.Collection[SbtProjectSettings]): Unit = {}
    override def onProjectsUnlinked(linkedProjectPaths: util.Set[String]): Unit = {}
    override def onBulkChangeStart(): Unit = {}
    override def onBulkChangeEnd(): Unit = {}
  }

  private def onProjectLoaded(project: Project): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode)
      return // otherwise, it can lead to project leaks in tests

    val dottyVersion = dottyVersionIn(project)

    if (!isShownIn(project)) {
      if (project.hasScala3 || dottyVersion.isDefined) {
        showDisclaimerIn(project,
          ScalaBundle.message("scala.3.support.is.experimental", "https://blog.jetbrains.com/scala/2020/03/17/scala-3-support-in-intellij-scala-plugin/"),
          configureUpdatesActionIn(project))
        setShownIn(project)
      }
    }

    if (!versionInfoShown) {
      dottyVersion.filter(_ != SupportedDottyVersion).foreach { unsupportedVersion =>
        showDisclaimerIn(project,
          ScalaBundle.message("scala.3.support.is.incompatible", unsupportedVersion, SupportedDottyVersion),
          useDottyVersionActionIn(project, SupportedDottyVersion), configureUpdatesActionIn(project))
      }
      versionInfoShown = true
    }
  }

  private def isShownIn(project: Project): Boolean =
    ScalaProjectSettings.getInstance(project).isScala3DisclaimerShown

  private def setShownIn(project: Project): Unit = {
    ScalaProjectSettings.getInstance(project).setScala3DisclaimerShown(true)
  }

  private def showDisclaimerIn(project: Project, message: String, actions: AnAction*): Unit = {
    val notification =
      ScalaNotificationGroups.stickyBalloonGroup
        .createNotification(message, NotificationType.INFORMATION)

    actions.foreach(notification.addAction)

    notification.setListener(new HyperlinkListener())

    Notifications.Bus.notify(notification)
  }

  private def configureUpdatesActionIn(project: Project) = new NotificationAction(ScalaBundle.message("configure.updates")) {
    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, classOf[ScalaProjectSettingsConfigurable],
        (_.selectUpdatesTab()): Consumer[ScalaProjectSettingsConfigurable])
    }
  }

  // TODO A workaround for the TODOs above (parsing build.sbt directly is not reliable, but is better than nothing).
  private def dottyVersionIn(project: Project): Option[String] = buildSbtIn(project).map(read).flatMap(dottyVersionIn)

  private def buildSbtIn(project: Project): Option[File] = Option(project.getBasePath).map(new File(_, "build.sbt")).filter(_.exists())

  private def dottyVersionIn(contents: String): Option[String] = DottyVersion.findFirstMatchIn(contents).map(_.group(1))

  private def read(file: File): String = new String(Files.readAllBytes(file.toPath))

  private def write(file: File, contents: String): Unit = Files.write(file.toPath, contents.getBytes)

  private def useDottyVersionActionIn(project: Project, version: String): NotificationAction = new NotificationAction(ScalaBundle.message("adjust.dotty.version", version)) {
    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
      buildSbtIn(project).foreach { file =>
        write(file, DottyVersion.replaceFirstIn(read(file), "scalaVersion := \"" + SupportedDottyVersion + "\""))
        ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id))
      }
    }
  }
}
