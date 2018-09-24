package org.jetbrains.sbt.settings

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.sbt.project.SbtProjectSystem

/**
  * @author Nikolay Obedin
  * @since 12/18/15.
  */
class StartupRoutine extends ApplicationInitializedListener {
  override def componentsInitialized(): Unit = {
    val key = SbtProjectSystem.Id + ExternalSystemConstants.USE_IN_PROCESS_COMMUNICATION_REGISTRY_KEY_SUFFIX
    Registry.get(key).setValue(true)
  }
}
