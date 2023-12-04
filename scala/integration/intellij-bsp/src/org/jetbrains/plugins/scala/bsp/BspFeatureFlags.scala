package org.jetbrains.plugins.scala.bsp

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.bsp.extension.points.BspConnectionDetailsGeneratorExtension

object BspFeatureFlags {
  private val BSP_INTEGRATION = "bsp.plugin.integration"
  val isBspPluginIntegrationEnabled = Registry.is(BSP_INTEGRATION)
}
