package org.jetbrains.bsp.project

import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager
import org.jetbrains.bsp.settings.BspExecutionSettings

class BspTaskManager extends ExternalSystemTaskManager[BspExecutionSettings] {
  // TODO bsp should be able to support this
  override def cancelTask(id: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean = false
}
