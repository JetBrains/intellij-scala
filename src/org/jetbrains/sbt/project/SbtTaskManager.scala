package org.jetbrains.sbt
package project

import java.util

import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager
import org.jetbrains.sbt.project.settings.SbtExecutionSettings

/**
 * @author Pavel Fatin
 */
class SbtTaskManager extends ExternalSystemTaskManager[SbtExecutionSettings] {
  def executeTasks(id: ExternalSystemTaskId, taskNames: util.List[String], projectPath: String, settings: SbtExecutionSettings,
                   vmOptions: util.List[String], scriptParameters: util.List[String], debuggerSetup: String, listener: ExternalSystemTaskNotificationListener) {}

  def cancelTask(id: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener) = false
}
