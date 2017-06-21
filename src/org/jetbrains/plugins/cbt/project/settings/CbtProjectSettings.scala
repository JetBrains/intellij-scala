package org.jetbrains.plugins.cbt.project.settings

import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.project.Project

import scala.beans.BeanProperty

class CbtProjectSettings extends ExternalProjectSettings {
  super.setUseAutoImport(true)

  @BeanProperty
  var linkCbtLibs = true

  override def clone(): CbtProjectSettings = {
    val result = new CbtProjectSettings()
    copyTo(result)
    result.linkCbtLibs = linkCbtLibs
    result
  }
}

object CbtProjectSettings {
  def getInstance(project: Project, path: String): CbtProjectSettings = {
    val sysetemSettings = CbtSystemSettings.getInstance(project)
    Option(sysetemSettings.getLinkedProjectSettings(path)).getOrElse(CbtProjectSettings.default)
  }

  def default: CbtProjectSettings = new CbtProjectSettings
}

