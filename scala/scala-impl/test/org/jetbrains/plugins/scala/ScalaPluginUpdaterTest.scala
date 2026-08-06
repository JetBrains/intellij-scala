package org.jetbrains.plugins.scala

import com.intellij.ide.plugins.{IdeaPluginDescriptorImpl, PluginManagerCore}
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.components.ScalaPluginUpdater

class ScalaPluginUpdaterTest extends SimpleTestCase {
  def testVersionPatcher(): Unit = {
    val pluginId = PluginId.getId("org.intellij.scala")
    val pluginDescriptor = PluginManagerCore.getPlugin(pluginId).asInstanceOf[IdeaPluginDescriptorImpl]
    val version = pluginDescriptor.getVersion

    val newVersion = "0.0.0"
    ScalaPluginUpdater.patchPluginVersion(newVersion, pluginDescriptor)
    val updatedVersion = pluginDescriptor.getVersion
    assert(newVersion == updatedVersion, "patchPluginVersion didn't work as expected")

    ScalaPluginUpdater.patchPluginVersion(version, pluginDescriptor)
  }
}
