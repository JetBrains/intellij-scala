package org.jetbrains.sbt
package project

import com.intellij.notification._
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListenerAdapter, ExternalSystemTaskType}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.sbt.settings.SbtSettings

import scala.jdk.CollectionConverters.{CollectionHasAsScala, SetHasAsJava}

// TODO Rely on the immediate UI interaction API when IDEA-123007 will be implemented
class SbtNotificationListener extends ExternalSystemTaskNotificationListenerAdapter {
  override def onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean): Unit = {
    // TODO this check must be performed in the External System itself (see SCL-7405)
    if (isSbtProject(id)) {
      processOutput(text)
    }
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

  override def onSuccess(id: ExternalSystemTaskId): Unit = {
    val isSbtProjectResolveTask = isSbtProject(id) && id.getType == ExternalSystemTaskType.RESOLVE_PROJECT
    val project = id.findProject
    if (isSbtProjectResolveTask && project != null) {
      val projectNode = ExternalSystemApiUtil.findProjectNode(project, SbtProjectSystem.Id, project.getBasePath)
      val linkedProjectSettings = SbtSettings.getInstance(project).getLinkedProjectSettings(project.getBasePath)
      if (projectNode != null && linkedProjectSettings != null) {
        val moduleDataNodes = ExternalSystemApiUtil.findAll(projectNode, ProjectKeys.MODULE).asScala
        val sbtNestedModulePaths = moduleDataNodes
          .flatMap(ExternalSystemApiUtil.findAll(_, Sbt.sbtNestedModuleDataKey).asScala)
          .map(_.getData.externalConfigPath).toSeq

        val externalModulePaths = sbtNestedModulePaths ++ linkedProjectSettings.getModules.asScala.toSeq
        linkedProjectSettings.setModules(externalModulePaths.toSet.asJava)
      }
    }
  }

  private def isSbtProject(id: ExternalSystemTaskId): Boolean = id.getProjectSystemId == SbtProjectSystem.Id
}

object WarningMessage {
  private val Prefix = "#warning: "

  def apply(text: String): String = Prefix + text

  def unapply(text: String): Option[String] = text.startsWith(Prefix).option(text.substring(Prefix.length))
}
