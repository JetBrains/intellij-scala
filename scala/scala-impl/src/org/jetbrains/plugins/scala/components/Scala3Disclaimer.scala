package org.jetbrains.plugins.scala.components

import java.util
import java.util.function.Consumer

import com.intellij.notification._
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.{Project, ProjectManagerListener}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.{ScalaProjectSettings, ScalaProjectSettingsConfigurable}
import org.jetbrains.plugins.scala.util.NotificationUtil.HyperlinkListener
import org.jetbrains.sbt.project.settings.{SbtProjectSettings, SbtProjectSettingsListener}

object Scala3Disclaimer {
  class ProjectListener extends ProjectManagerListener {
    override def projectOpened(project: Project): Unit = {
      onProjectLoaded(project) // for IDEA-based projects
    }
  }

  // TODO Re-imports are not detected.
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
    if (!isShownIn(project)) {
      if (project.hasScala3) {
        showDisclaimerIn(project)
        setShownIn(project)
      }
    }
  }

  private def isShownIn(project: Project): Boolean =
    ScalaProjectSettings.getInstance(project).isScala3DisclaimerShown

  private def setShownIn(project: Project): Unit = {
    ScalaProjectSettings.getInstance(project).setScala3DisclaimerShown(true)
  }

  private def showDisclaimerIn(project: Project): Unit = {
    val notification =
      new NotificationGroup(ScalaBundle.message("scala.3.disclaimer"), NotificationDisplayType.STICKY_BALLOON, /* isLogByDefault = */ true)
        .createNotification(
          ScalaBundle.message("scala.3.support.is.experimental", "https://blog.jetbrains.com/scala/2020/03/17/scala-3-support-in-intellij-scala-plugin/"),
          NotificationType.INFORMATION)
        .addAction(new NotificationAction(ScalaBundle.message("configure.updates")) {
          override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, classOf[ScalaProjectSettingsConfigurable],
              (_.selectUpdatesTab()): Consumer[ScalaProjectSettingsConfigurable])
          }
        })

    notification.setListener(new HyperlinkListener())

    Notifications.Bus.notify(notification)
  }
}
