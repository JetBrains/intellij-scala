package org.jetbrains.bsp

import java.util.MissingResourceException

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.util.registry.Registry

class StartupRoutine extends ApplicationInitializedListener {

  override def componentsInitialized(): Unit = {

    val bspEnabled = Registry.get(bsp.RegistryKeyFeatureEnabled)

    // bsp is experimental feature, hide it from UI by default
    val shouldSetKey =
      try { ! bspEnabled.isChangedFromDefault }
      catch { case _ : MissingResourceException => true }

    if (shouldSetKey)
      bspEnabled.setValue(false)

    // use in-process mode for external system
    Registry
      .get(bsp.ProjectSystemId + ExternalSystemConstants.USE_IN_PROCESS_COMMUNICATION_REGISTRY_KEY_SUFFIX)
      .setValue(true)

  }

}

