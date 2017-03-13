package org.jetbrains.sbt.resolvers

import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListenerAdapter}

/**
  * @author mutcianm
  * @since 13.03.17.
  */
class SbtRefreshFinishedListener extends ExternalSystemTaskNotificationListenerAdapter{
  override def onSuccess(id: ExternalSystemTaskId) = {
    val project = id.findProject()
    if (project != null && !project.isDisposed) {
      SbtIndexesManager.getInstance(project).updateLocalIvyIndex()
    }
  }
}
