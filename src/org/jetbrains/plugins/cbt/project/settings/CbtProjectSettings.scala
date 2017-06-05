package org.jetbrains.plugins.cbt.project.settings

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.project.Project

class CbtProjectSettings extends ExternalProjectSettings{
  super.setUseAutoImport(true)

  override def clone(): CbtProjectSettings = {
    val result = new CbtProjectSettings()
    copyTo(result)
    result
  }
}

object CbtProjectSettings {
  def getInstance(project: Project): CbtProjectSettings =
    ServiceManager.getService(project, classOf[CbtProjectSettings])

  def default: CbtProjectSettings = new CbtProjectSettings
}

