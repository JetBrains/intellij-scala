package org.jetbrains.plugins.scala.components

import com.intellij.notification._
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.{Project, ProjectManager, ProjectManagerListener}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.settings.sections.UpdateSettingsSectionConfigurable
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups

object Scala3Disclaimer {
  private final class ProjectListener extends ProjectManagerListener {
    override def projectOpened(project: Project): Unit = {
      onProjectLoaded(project) // for IDEA-based projects
    }
  }
  class DumbModeListener extends com.intellij.openapi.project.DumbService.DumbModeListener {
    override def exitDumbMode(): Unit = {
      ProjectManager.getInstance().getOpenProjects.foreach(onProjectLoaded) // for external system projects
    }
  }

  private def onProjectLoaded(project: Project): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode)
      return // otherwise, it can lead to project leaks in tests

    if (!isShownIn(project) && project.hasScala3 && !ScalaPluginUpdater.pluginIsNightly) {
      showDisclaimerIn(ScalaBundle.message("scala.3.support.is.experimental"),
        configureUpdatesActionIn(project))
      setShownIn(project)
    }
  }

  private def isShownIn(project: Project): Boolean =
    ScalaProjectSettings.getInstance(project).isScala3DisclaimerShown

  private def setShownIn(project: Project): Unit = {
    ScalaProjectSettings.getInstance(project).setScala3DisclaimerShown(true)
  }

  private def showDisclaimerIn(message: String, actions: AnAction*): Unit = {
    val notification =
      ScalaNotificationGroups.scala3Disclaimer
        .createNotification(message, NotificationType.INFORMATION)

    actions.foreach(notification.addAction)
    Notifications.Bus.notify(notification)
  }

  private def configureUpdatesActionIn(project: Project) = new NotificationAction(ScalaBundle.message("configure.updates")) {
    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, classOf[UpdateSettingsSectionConfigurable])
    }
  }
}
