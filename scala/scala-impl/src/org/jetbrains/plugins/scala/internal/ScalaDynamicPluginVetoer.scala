package org.jetbrains.plugins.scala.internal

import com.intellij.ide.plugins.{DynamicPluginVetoer, IdeaPluginDescriptor}
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier

class ScalaDynamicPluginVetoer extends DynamicPluginVetoer {
  @Nullable
  private val allowUnload: String = null

  override def vetoPluginUnload(ideaPluginDescriptor: IdeaPluginDescriptor): String = {
    /*
      This method is called before the plugin is loaded or unloaded,
      or better said it *would* be called before the plugin is loaded.
      Of course, this class is not registered before the plugin is loaded,
      so this method cannot be called.

      Returning the error message below is the single point that prohibits
      idea from attempting to dynamically unload this plugin. Even though we fulfill
      all formal requirements, unloading the plugin would not work because we cannot untangle multiple
      classes from the platform (SCL-16809)

      In [ScalaPluginDynamicLoadingTest] we still test that the plugin fulfills all the formal
      requirements to load/unload the plugin (for example, whether all our extension points are marked as dynamic).
      That is important so the plugin can be loaded dynamically.
      Because in that test the plugin is already loaded, this method is still being called,
      but would return the error message, which would make the test fail.
      So we just return here when we are in UnitTestMode.
     */
    if (ApplicationManager.getApplication.isUnitTestMode) {
      return allowUnload
    }

    if (ideaPluginDescriptor.getPluginId == ScalaPluginVersionVerifier.scalaPluginId) {
      "Dynamically unloading the JetBrains Scala plugin is not supported yet"
    } else {
      allowUnload
    }
  }
}
