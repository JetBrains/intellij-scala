package org.jetbrains.plugins.cbt.project

import java.util

import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager
import org.jetbrains.plugins.cbt.project.settings.CbtExecutionSettings

class CbtTaskManager extends ExternalSystemTaskManager[CbtExecutionSettings] {
  override def executeTasks(id: ExternalSystemTaskId,
                   taskNames: util.List[String],
                   projectPath: String,
                   settings: CbtExecutionSettings,
                   vmOptions: util.List[String],
                   scriptParameters: util.List[String],
                   debuggerSetup: String,
                   listener: ExternalSystemTaskNotificationListener): Unit = {}

  override def cancelTask(id: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean = false
}
