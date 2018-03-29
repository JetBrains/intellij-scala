package org.jetbrains.plugins.scala.project.migration

import com.intellij.openapi.externalSystem.model.task._

/**
  * User: Dmitry.Naydanov
  * Date: 14.11.16.
  */
class MigratorsSbtImportListener extends ExternalSystemTaskNotificationListenerAdapter {

  import MigratorsSbtImportListener._

  override def onSuccess(id: ExternalSystemTaskId): Unit =
    onCodeStoreComponent(id) { component =>
      component.notifyImportFinished()
    }

  override def onStart(id: ExternalSystemTaskId): Unit =
    onCodeStoreComponent(id) { component =>
      component.onImportAboutToStart()
    }

  override def onQueued(id: ExternalSystemTaskId, workingDir: String): Unit = onStart(id)
}

object MigratorsSbtImportListener {

  private def onCodeStoreComponent(id: ExternalSystemTaskId)
                                  (action: BundledCodeStoreComponent => Unit): Unit =
    for {
      project <- findProject(id)
      component = BundledCodeStoreComponent.getInstance(project)
    } action(component)

  private[this] def findProject(id: ExternalSystemTaskId) = id.getType match {
    case ExternalSystemTaskType.RESOLVE_PROJECT => Option(id.findProject)
    case _ => None
  }
}