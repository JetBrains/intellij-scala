package org.jetbrains.bsp

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.util.registry.Registry

class BspStartupRoutine extends ApplicationInitializedListener {

  override def componentsInitialized(): Unit = {

    // use in-process mode for external system
    Registry
      .get(BSP.ProjectSystemId + ExternalSystemConstants.USE_IN_PROCESS_COMMUNICATION_REGISTRY_KEY_SUFFIX)
      .setValue(true)

  }

}

