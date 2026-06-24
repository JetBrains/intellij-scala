package org.jetbrains.plugins.scala.internal

import com.intellij.ide.plugins.{DynamicPlugins, PluginMainDescriptor, PluginManagerCore}
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers

//noinspection ApiStatus,UnstableApiUsage
class ScalaPluginDynamicLoadingTest extends ScalaFixtureTestCase with AssertionMatchers {
  def test_ScalaPluginIsLoadableInPrinciple(): Unit = {
    val scalaPluginDescriptor =
      PluginManagerCore.getPlugin(PluginId.getId("org.intellij.scala"))
    val scalaPluginMainDescriptor =
      scalaPluginDescriptor
        .asOptionOf[PluginMainDescriptor]
        .getOrElse(throw new AssertionError(s"Expected descriptor to be PluginMainDescriptor but was $scalaPluginDescriptor"))

    val result = DynamicPlugins.INSTANCE.checkCanLoadWithoutRestart(scalaPluginMainDescriptor)

    /*
     * If this fails, there was done something that doesn't fulfill the formal requirements for dynamic plugins.
     * Most likely, an extension point was added that was not marked with dynamic="true"
     *  Also, the log should contain the reason why DynamicPlugins.checkCanLoadWithoutRestart returned false
     */
    result shouldBe true
  }
}
