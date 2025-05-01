package org.jetbrains.sbt.project

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.util.RunOnceUtil
import com.intellij.notification.{Notification, NotificationAction, NotificationGroupManager, NotificationType, Notifications}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.ExternalSystemConfigurableAware
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.service.settings.AbstractExternalSystemConfigurable
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.sbt.project.SeparateMainTestModulesNotificationListener._
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.{SbtBundle, SbtUtil}

/**
 * Listener that handles the display of notifications for separate main/test modules feature
 * after project data import is completed.
 *
 * The notification will inform users about the separate modules setting for main and test sources,
 * providing options to:
 *  - Read more about this feature in the blog post
 *  - Access sbt project settings to revert this if needed
 */
class SeparateMainTestModulesNotificationListener(project: Project) extends ProjectDataImportListener {

  override def onImportFinished(projectPath: String): Unit = {
    val sbtProjectSettings = SbtProjectSettings.`for`(project, projectPath)
    sbtProjectSettings.foreach(showNotificationIfNecessary(_, project))
  }
}

object SeparateMainTestModulesNotificationListener {
  private val Key = "sbt.separate.main.test.modules.notification.shown"

  /**
   * Displays the separate modules notification if all the following conditions are met:
   *  - The project is trusted
   *  - The project is not in preview mode
   *  - The separate sources setting is enabled but not explicitly set by user
   *  - The notification hasn't been shown before
   */
  def showNotificationIfNecessary(sbtProjectSettings: SbtProjectSettings, project: Project): Unit =
    if (shouldShow(sbtProjectSettings, project)) {
      RunOnceUtil.runOnceForApp(Key, () => show(sbtProjectSettings.getExternalProjectPath, project))
    }

  private def shouldShow(sbtProjectSettings: SbtProjectSettings, project: Project): Boolean = {
    val isTrusted = TrustedProjects.isProjectTrusted(project)
    isTrusted && {
      val isPreview = SbtUtil.isPreview(project, sbtProjectSettings.getExternalProjectPath)
      !isPreview && sbtProjectSettings.separateProdAndTestSources && !sbtProjectSettings.separateProdAndTestSourcesIsExplicit
    }
  }

  private def show(projectPath: String, project: Project): Unit = {
    val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("sbt.main.test.modules.enabled")
    val notification = notificationGroup
      .createNotification(
        SbtBundle.message("separate.modules.main.test.notification.title"),
        SbtBundle.message("separate.modules.main.test.notification"),
        NotificationType.INFORMATION
      )

    val readMoreAction = createNotificationAction(
      action = SbtUtil.openSeparateMainTestModulesBlogPost(),
      text = SbtBundle.message("separate.prod.test.modules.link.text")
    )
    val openSbtSettingsAction = createNotificationAction(
      action = openSbtProjectSettings(project, projectPath),
      text = SbtBundle.message("open.sbt.project.settngs")
    )

    Seq(readMoreAction, openSbtSettingsAction).foreach(notification.addAction)

    Notifications.Bus.notify(notification)
  }

  private def createNotificationAction(action: => Unit, @Nls text: String): NotificationAction = new NotificationAction(text) {
    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = action
  }

  private def openSbtProjectSettings(project: Project, externalProjectPath: String): Unit = {
    val manager = ExternalSystemApiUtil.getManager(SbtProjectSystem.Id)
    val configurable = manager.asInstanceOf[ExternalSystemConfigurableAware].getConfigurable(project)
    configurable match {
      case x: AbstractExternalSystemConfigurable[_, _, _] =>
        ShowSettingsUtil.getInstance().editConfigurable(project, x, () => x.selectProject(externalProjectPath))
      case _ =>
    }
  }
}
