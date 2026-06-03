package org.jetbrains.plugins.scala.compiler.sync

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

abstract class ExternalSystemBasedProjectSyncHandler(projectSystemId: ProjectSystemId) extends BuildToolProjectSyncHelper {
  override def projectSyncHandler(project: Project): Option[ProjectSyncHandler] = {
    val modules = ModuleManager.getInstance(project).getModules
    val handles = modules.exists(ExternalSystemApiUtil.isExternalSystemAwareModule(projectSystemId, _))
    if (handles) Some(ProjectSyncHandler.ExternalSystem) else None
  }
}
