package org.jetbrains.sbt.project

import com.intellij.notification.{Notification, NotificationAction, NotificationType, NotificationsManager}
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.Nullable
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.settings.ShouldUpdateRunConfigurations

import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava

class UpdateRunConfigurationsNotification(
  project: Project,
  isDowngradingFromSeparateMainTestModules: Option[Boolean]
) extends Notification("sbt.configuration.migration", SbtBundle.message("sbt.configuration.migration.notification.content"), NotificationType.WARNING) {

  private val isSuggestion = Registry.is("sbt.configuration.update.suggestion")

  override def getActions: util.List[AnAction] = {
    val updateAction = createUpdateAction
    val ignoreAction = NotificationAction.createSimpleExpiring(SbtBundle.message("sbt.configuration.migration.notification.ignore.text"), () => {
      ShouldUpdateRunConfigurations.disableConfigurationUpdate(project)
    })
    List(updateAction, ignoreAction).asJava
  }

  override def isSuggestionType: Boolean = isSuggestion

  private def createUpdateAction: AnAction =
    NotificationAction.create(SbtBundle.message("sbt.migrate.configurations.text"), (_: AnActionEvent, notification: Notification) => {
      ShouldUpdateRunConfigurations.disableConfigurationUpdate(project)

      val areAllConfigurationsUpdated = SbtMigrateConfigurationsAction.updateRunConfigurations(project, isDowngradingFromSeparateMainTestModules)
      if (areAllConfigurationsUpdated || !isSuggestion) {
        // This will remove the notification completely (closing the balloon and removing it from the tool window)
        notification.expire()
      } else {
        // This will only close the balloon, and the notification will remain active in the tool window.
        // If the balloon doesn't exist, then simply nothing will happen (the notification will remain active in the tool window)
        Option(notification.getBalloon).foreach(_.hide(true))
      }
    })
}

object UpdateRunConfigurationsNotification {

  def closeAllExistingSuggestions(@Nullable project: Project): Unit =
    getAllUpdateConfigurationsNotifications(project).filter(_.isSuggestionType).foreach(_.expire)

  def closeAllExistingNotifications(@Nullable project: Project): Unit =
    getAllUpdateConfigurationsNotifications(project).foreach(_.expire)

  private def getAllUpdateConfigurationsNotifications(project: Project): Seq[UpdateRunConfigurationsNotification] =
    NotificationsManager.getNotificationsManager.getNotificationsOfType(classOf[UpdateRunConfigurationsNotification], project).toSeq
}
