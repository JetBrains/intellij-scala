package org.jetbrains.plugins.scala.project.migration

import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListenerAdapter, ExternalSystemTaskType}
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.project.settings.SbtLocalSettings

/**
  * User: Dmitry.Naydanov
  * Date: 14.11.16.
  */
class MigratorsSbtImportListener extends ExternalSystemTaskNotificationListenerAdapter {
  private val myId = ExternalSystemTaskType.RESOLVE_PROJECT
  
  private def ifMyTask(id: ExternalSystemTaskId, action: (Project, SbtLocalSettings) => Unit) {
    if (id.getType != myId) return
    
    for {
      project <- Option(id.findProject())
      settings <- Option(SbtLocalSettings.getInstance(project))
    } {
      action(project, settings)
    }
    
  }
  
  override def onSuccess(id: ExternalSystemTaskId): Unit = {
    ifMyTask(id, (project, _) => BundledCodeStoreComponent.getInstance(project).notifyImportFinished())
  }

  override def onStart(id: ExternalSystemTaskId): Unit = {
    ifMyTask(id, (project, _) => BundledCodeStoreComponent.getInstance(project).onImportAboutToStart())
  }

  override def onQueued(id: ExternalSystemTaskId, workingDir: String): Unit = onStart(id)
}
