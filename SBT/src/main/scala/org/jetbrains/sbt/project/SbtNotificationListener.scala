package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListenerAdapter}
import com.intellij.notification.{NotificationType, Notifications, Notification}

/**
 * @author Pavel Fatin
 */
// TODO Rely on the immediate UI interaction API when IDEA-123007 will be implemented
class SbtNotificationListener extends ExternalSystemTaskNotificationListenerAdapter {
  override def onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
    text match {
      case WarningMessage(message) =>
        Notifications.Bus.notify(new Notification("scala", "SBT project import", message, NotificationType.WARNING))
    }
  }
}

object WarningMessage {
  private val Prefix = "#warning: "

  def apply(text: String): String = Prefix + text

  def unapply(text: String): Option[String] = text.startsWith(Prefix).option(text.substring(Prefix.length))
}
