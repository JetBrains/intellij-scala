package org.jetbrains.plugins.scala.internal

import com.intellij.ide.plugins.{CannotUnloadPluginException, DynamicPluginListener, IdeaPluginDescriptor}
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier

class ScalaPluginDynamicUnloadDenier extends DynamicPluginListener {
  override def checkUnloadPlugin(pluginDescriptor: IdeaPluginDescriptor): Unit = {
    /*
      This method is called before the plugin is loaded or unloaded,
      or better said it *would* be called before the plugin is loaded.
      Of course, this class is not registered before the plugin is loaded,
      so this method cannot be called.

      Throwing the CannotUnloadPluginException below is the single point that prohibits
      idea from attempting to dynamically unload this plugin. Even though we fulfill
      all formal requirements, unloading the plugin would not work because we cannot untangle multiple
      classes from the platform (SCL-16809)

      In [ScalaPluginDynamicLoadingTest] we still test that the plugin fulfills all the formal
      requirements to load/unload the plugin (for example, whether all our extension points are marked as dynamic).
      That is important so the plugin can be loaded dynamically.
      Because in that test the plugin is already loaded, this method is still being called,
      but would throw the CannotUnloadPluginException, which would make the test fail.
      So we just return here when we are in UnitTestMode.
     */
    if (ApplicationManager.getApplication.isUnitTestMode) {
      return
    }

    if (pluginDescriptor.getPluginId == ScalaPluginVersionVerifier.scalaPluginId) {
      throw new CannotUnloadPluginException("Dynamically unloading the JetBrains Scala plugin is not supported yet")
    }
  }
}
