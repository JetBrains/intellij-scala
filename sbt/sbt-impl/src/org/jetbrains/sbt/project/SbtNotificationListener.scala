package org.jetbrains.sbt
package project

import com.intellij.notification._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListenerAdapter, ExternalSystemTaskType}
import org.jetbrains.plugins.scala.project.ScalaProjectConfigurationService

// TODO Rely on the immediate UI interaction API when IDEA-123007 will be implemented
class SbtNotificationListener extends ExternalSystemTaskNotificationListenerAdapter {
  override def onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean): Unit = {
    // TODO this check must be performed in the External System itself (see SCL-7405)
    if (isSbtProject(id)) {
      processOutput(text)
    }
  }

  override def onStart(id: ExternalSystemTaskId, workingDir: String): Unit = {
    if (id.getType != ExternalSystemTaskType.RESOLVE_PROJECT) return
    val project = id.findProject()
    if (project == null) return

    ScalaProjectConfigurationService.getInstance(project).onSyncStarted()
    ScalaProjectConfigurationService.getInstance(project).refreshEditorNotifications()
  }

  override def onEnd(id: ExternalSystemTaskId): Unit = {
    if (id.getType != ExternalSystemTaskType.RESOLVE_PROJECT) return
    val project = id.findProject()
    if (project == null) return

    ScalaProjectConfigurationService.getInstance(project).onSyncEnded()
  }

  private def processOutput(text: String): Unit = {
    text match {
      case WarningMessage(message) =>
        val title = SbtBundle.message("sbt.project.import")
        //noinspection ReferencePassedToNls
        Notifications.Bus.notify(new Notification(title, title, message, NotificationType.WARNING))
      case _ => // do nothing
    }
  }

  private def isSbtProject(id: ExternalSystemTaskId): Boolean = id.getProjectSystemId == SbtProjectSystem.Id
}

object WarningMessage {
  private val Prefix = "#warning: "

  def apply(text: String): String = Prefix + text

  def unapply(text: String): Option[String] = text.startsWith(Prefix).option(text.substring(Prefix.length))
}
