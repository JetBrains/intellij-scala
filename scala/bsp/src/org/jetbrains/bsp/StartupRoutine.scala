package org.jetbrains.bsp

import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.util.registry.Registry

class StartupRoutine extends ApplicationComponent {
  override def initComponent(): Unit = {
    setUpExternalSystemToPerformImportInIdeaProcess()
  }

  private def setUpExternalSystemToPerformImportInIdeaProcess(): Unit =
    Registry
      .get(bsp.ProjectSystemId + ExternalSystemConstants.USE_IN_PROCESS_COMMUNICATION_REGISTRY_KEY_SUFFIX)
      .setValue(true)
}

