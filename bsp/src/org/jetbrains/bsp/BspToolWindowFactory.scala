package org.jetbrains.bsp

import com.intellij.openapi.externalSystem.service.task.ui.AbstractExternalSystemToolWindowFactory
import com.intellij.openapi.project.Project

class BspToolWindowFactory extends AbstractExternalSystemToolWindowFactory(BSP.ProjectSystemId) {
  override def isApplicable(project: Project): Boolean = {
    // TODO figure out if we should check if it's really a bsp project
    super.isApplicable(project)
  }
}
