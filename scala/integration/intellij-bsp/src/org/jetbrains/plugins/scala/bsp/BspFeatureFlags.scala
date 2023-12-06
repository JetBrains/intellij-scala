package org.jetbrains.plugins.scala.bsp

import com.intellij.openapi.util.registry.Registry

object BspFeatureFlags {
  private val BSP_INTEGRATION = "bsp.plugin.integration"
  val isBspPluginIntegrationEnabled = Registry.is(BSP_INTEGRATION)
}
