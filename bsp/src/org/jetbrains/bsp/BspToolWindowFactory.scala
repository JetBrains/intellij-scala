package org.jetbrains.bsp

import com.intellij.openapi.externalSystem.service.task.ui.AbstractExternalSystemToolWindowFactory
import com.intellij.openapi.project.Project

class BspToolWindowFactory extends AbstractExternalSystemToolWindowFactory(BSP.ProjectSystemId) {
  override def isApplicable(project: Project): Boolean =
    BspUtil.isBspProject(project)
}
