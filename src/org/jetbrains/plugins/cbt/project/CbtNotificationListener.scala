package org.jetbrains.plugins.cbt.project

import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListenerAdapter}
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationCategory, NotificationData, NotificationSource}
import org.jetbrains.plugins.cbt.project.structure.CbtProjectImporingException
import org.jetbrains.sbt.project.SbtProjectSystem

class CbtNotificationListener extends ExternalSystemTaskNotificationListenerAdapter {
  override def onFailure(id: ExternalSystemTaskId, e: Exception): Unit = {
    if (id.getProjectSystemId == CbtProjectSystem.Id) {
      e match {
        case importEx: CbtProjectImporingException =>
          val title = "CBT project importing failure"
          val text = importEx.getMessage
          Notifications.Bus.notify(new Notification(title, title, text, NotificationType.ERROR))
      }
    }
  }
}
