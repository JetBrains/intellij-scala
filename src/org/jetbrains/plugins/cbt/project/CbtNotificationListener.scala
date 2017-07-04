package org.jetbrains.plugins.cbt.project

import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListenerAdapter}
import org.jetbrains.plugins.cbt.project.structure.CbtProjectImporingException

class CbtNotificationListener extends ExternalSystemTaskNotificationListenerAdapter{
  override def onFailure(id: ExternalSystemTaskId, e: Exception): Unit = {
    if (id.getProjectSystemId == CbtProjectSystem.Id) {
      e match {
        case importEx: CbtProjectImporingException =>
          val title = "CBT project importing failure"
          Notifications.Bus.notify(new Notification(title, title, e.getMessage, NotificationType.ERROR))
      }
    }
  }
}
