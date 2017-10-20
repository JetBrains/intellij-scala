package org.jetbrains.plugins.cbt.project.settings

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.project.CbtProjectSystem

class CbtLocalSettings(project: Project)
  extends AbstractExternalSystemLocalSettings(CbtProjectSystem.Id, project)

object CbtLocalSettings {
  def getInstance(project: Project): CbtLocalSettings =
    ServiceManager.getService(project, classOf[CbtLocalSettings])
}