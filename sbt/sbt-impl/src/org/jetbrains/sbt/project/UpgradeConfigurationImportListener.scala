package org.jetbrains.sbt.project

import com.intellij.notification.{NotificationAction, NotificationGroupManager, NotificationType}
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.{SbtBundle, SbtUtil}

class UpgradeConfigurationImportListener(project: Project) extends ProjectDataImportListener {

  override def onImportFinished(projectPath: String): Unit = {
    val isNew = project.getUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT)
    if (isNew != null && isNew) setNotificationShown()
    val isConfigurationCheckNotNeeded = isNotificationAlreadyShown || !SbtUtil.isSbtProject(project)
    if (isConfigurationCheckNotNeeded) return

    if (areIncorrectConfigurationsPresent()) showNotification()
    setNotificationShown()
  }

  private def showNotification():Unit = {
    val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("sbt.configuration.migration")
    val notification = notificationGroup.createNotification(SbtBundle.message("sbt.configuration.migration.notification.content"), NotificationType.WARNING)

    val action = ActionManager.getInstance.getAction(SbtMigrateConfigurationsAction.ID)
    notification.addAction(action)

    val ignoreAction = NotificationAction.createSimpleExpiring(SbtBundle.message("sbt.configuration.migration.notification.ignore.text"), () => notification.expire())
    notification.addAction(ignoreAction)

    notification.notify(project)
  }

  private def isNotificationAlreadyShown: Boolean =
    ScalaProjectSettings.getInstance(project).isMigrateConfigurationsNotificationShown

  private def setNotificationShown(): Unit =
    ScalaProjectSettings.getInstance(project).setMigrateConfigurationsNotificationShown(true)

  private def areIncorrectConfigurationsPresent(): Boolean = {
    val moduleBasedConfigurations = SbtUtil.getAllModuleBasedConfigurationsInProject(project)

    moduleBasedConfigurations.exists { config =>
      val configurationModule = config.getConfigurationModule
      val oldModuleName = configurationModule.getModuleName
      // note: when configuration doesn't have a module, then moduleName is empty
      oldModuleName.nonEmpty && configurationModule.getModule == null
    }
  }
}
