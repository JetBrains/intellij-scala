package org.jetbrains.sbt
package project

import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListenerAdapter}
import org.jetbrains.sbt.project.settings.SbtLocalSettings

/**
 * @author Pavel Fatin
 */
// TODO Rely on the immediate UI interaction API when IDEA-123007 will be implemented
class SbtNotificationListener extends ExternalSystemTaskNotificationListenerAdapter {
  override def onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
    // TODO this check must be performed in the External System itself (see SCL-7405)
    if (id.getProjectSystemId == SbtProjectSystem.Id) {
      processOutput(text)
    }
  }

  override def onSuccess(id: ExternalSystemTaskId): Unit = {
    for {
      project  <- Option(id.findProject())
      settings <- Option(SbtLocalSettings.getInstance(project))
    } {
      settings.lastUpdateTimestamp = System.currentTimeMillis()
    }
  }

  private def processOutput(text: String) {
    text match {
      case WarningMessage(message) =>
        Notifications.Bus.notify(new Notification("scala", "SBT project import", message, NotificationType.WARNING))
      case _ => // do nothing
    }
  }
}

object WarningMessage {
  private val Prefix = "#warning: "

  def apply(text: String): String = Prefix + text

  def unapply(text: String): Option[String] = text.startsWith(Prefix).option(text.substring(Prefix.length))
}
