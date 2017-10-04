package org.jetbrains.plugins.cbt.settings

import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.cbt.project.CbtProjectSystem

class StartupRoutine extends ApplicationComponent {
  override def initComponent(): Unit = {
    setUpExternalSystemToPerformImportInIdeaProcess()
  }

  private def setUpExternalSystemToPerformImportInIdeaProcess(): Unit =
    Registry.get(CbtProjectSystem.Id +
      ExternalSystemConstants.USE_IN_PROCESS_COMMUNICATION_REGISTRY_KEY_SUFFIX).setValue(true)
}
