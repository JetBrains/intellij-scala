package org.jetbrains.plugins.scala.internal

import com.intellij.ide.plugins.{DynamicPluginListener, IdeaPluginDescriptor}
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier

import java.util.concurrent.atomic.AtomicBoolean

class ScalaDynamicPluginManager extends DynamicPluginListener {
  override def beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean): Unit =
    if (pluginDescriptor.getPluginId == ScalaPluginVersionVerifier.scalaPluginId) {
      ScalaDynamicPluginManager._isScalaPluginUnloading.set(true)
    }

  override def pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean): Unit =
    if (pluginDescriptor.getPluginId == ScalaPluginVersionVerifier.scalaPluginId) {
      ScalaDynamicPluginManager._isScalaPluginUnloading.set(false)
    }
}

object ScalaDynamicPluginManager {
  private val _isScalaPluginUnloading = new AtomicBoolean(false)

  def isScalaPluginUnloading: Boolean = _isScalaPluginUnloading.get()
}
