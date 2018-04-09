package org.jetbrains.plugins.scala.components.libextensions

import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListenerAdapter}
import com.intellij.openapi.module.ModuleManager
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.project.module.SbtModule
import org.jetbrains.sbt.resolvers.SbtResolver

class LibraryExtensionsSBTListener extends ExternalSystemTaskNotificationListenerAdapter {

  private var allProjectResolvers: Set[SbtResolver] = Set.empty

  override def onSuccess(id: ExternalSystemTaskId): Unit = {
    val project = id.findProject()
    if (project == null || project.isDisposed) return
    if (!ScalaProjectSettings.getInstance(project).isEnableLibraryExtensions) return
    ModuleManager.getInstance(project).getModules.foreach {
      allProjectResolvers ++= SbtModule.getResolversFrom(_)
    }

    LibraryExtensionsManager.getInstance(project).searchExtensions(allProjectResolvers)
  }
}