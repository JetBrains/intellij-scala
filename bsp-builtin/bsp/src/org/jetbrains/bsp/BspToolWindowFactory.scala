package org.jetbrains.bsp

import com.intellij.openapi.externalSystem.service.task.ui.AbstractExternalSystemToolWindowFactory
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings
import com.intellij.openapi.project.Project
import org.jetbrains.bsp.settings.BspSettings

class BspToolWindowFactory extends AbstractExternalSystemToolWindowFactory(BSP.ProjectSystemId) {

  override def getSettings(project: Project): AbstractExternalSystemSettings[_, _, _] =
    BspSettings.getInstance(project)
}
