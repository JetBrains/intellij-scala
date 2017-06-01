package org.jetbrains.plugins.cbt.project.settings

import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings

class CbtProjectSettings extends ExternalProjectSettings{
  super.setUseAutoImport(false)

  override def clone(): CbtProjectSettings = {
    val result = new CbtProjectSettings()
    copyTo(result)
    result
  }
}

object SbtProjectSettings {
  def default: CbtProjectSettings =
    new CbtProjectSettings
}