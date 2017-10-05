package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.extensions.{ExtensionPointName, PluginAware, PluginDescriptor}
import com.intellij.util.xmlb.annotations.Attribute


class NailgunServerAdditionalCp extends PluginAware {
  private var myPluginDescriptor: PluginDescriptor = null
  private var myClasspath: String = null

  /**
    * Specifies semicolon-separated list of paths which should be added to the classpath of the nailgun compile server.
    * The paths are relative to the plugin 'lib' directory.
    */
  @Attribute("classpath") def getClasspath: String = myClasspath

  def setClasspath(classpath: String): Unit = myClasspath = classpath

  def getPluginDescriptor: PluginDescriptor = myPluginDescriptor

  override def setPluginDescriptor(pluginDescriptor: PluginDescriptor): Unit = {
    myPluginDescriptor = pluginDescriptor
  }
}

object NailgunServerAdditionalCp {
  val EP_NAME: ExtensionPointName[NailgunServerAdditionalCp] =
    ExtensionPointName.create("org.intellij.scala.nailgunServerAdditionalCp")
}