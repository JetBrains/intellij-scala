package org.jetbrains.plugins.scala.internal

import com.intellij.ide.plugins.{DynamicPlugins, IdeaPluginDescriptorImpl, IdeaPluginDescriptorImplKt, PluginMainDescriptor, PluginManagerCore}
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers
import org.junit.Assert.assertTrue

//noinspection ApiStatus,UnstableApiUsage
class ScalaPluginDynamicLoadingTest extends ScalaFixtureTestCase with AssertionMatchers {
  def test_ScalaPluginIsLoadableInPrinciple(): Unit = {
    val scalaPluginDescription =
      PluginManagerCore.getPlugin(PluginId.getId("org.intellij.scala"))

    val scalaPluginDescriptionImpl: IdeaPluginDescriptorImpl =
      scalaPluginDescription
        .asOptionOf[IdeaPluginDescriptorImpl]
        .getOrElse(throw new AssertionError(s"Expected descriptor to be IdeaPluginDescriptorImpl but was $scalaPluginDescription"))

    val scalaPluginMainDescriptor: PluginMainDescriptor =
      IdeaPluginDescriptorImplKt.getMainDescriptor(scalaPluginDescriptionImpl)

    val canLoadWithoutRestart = DynamicPlugins.INSTANCE.checkCanLoadWithoutRestart(scalaPluginMainDescriptor)

    /*
     * If this fails, there was done something that doesn't fulfill the formal requirements for dynamic plugins.
     * Most likely, an extension point was added that was not marked with dynamic="true"
     *  Also, the log should contain the reason why DynamicPlugins.checkCanLoadWithoutRestart returned false
     */
    assertTrue(
      s"Scala plugin should be loadable in principle",
      canLoadWithoutRestart
    )
  }
}
