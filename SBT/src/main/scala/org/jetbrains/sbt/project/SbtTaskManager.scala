package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager
import org.jetbrains.sbt.project.settings.SbtExecutionSettings
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskNotificationListener, ExternalSystemTaskId}
import java.util

/**
 * @author Pavel Fatin
 */
class SbtTaskManager extends ExternalSystemTaskManager[SbtExecutionSettings] {
  def executeTasks(id: ExternalSystemTaskId, taskNames: util.List[String], projectPath: String, settings: SbtExecutionSettings,
                   vmOptions: String, debuggerSetup: String, listener: ExternalSystemTaskNotificationListener) {}

  def cancelTask(id: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener) = false
}
