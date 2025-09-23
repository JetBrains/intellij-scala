package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.extensions.{PluginAware, PluginDescriptor}
import com.intellij.util.xmlb.annotations.{Attribute, Transient}
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

class CompileServerClasspathProvider extends PluginAware {
  private var myPluginDescriptor: PluginDescriptor = _
  private var myClasspath: String = ""

  /**
    * Specifies semicolon-separated list of paths which should be added to the classpath of the nailgun compile server.
    * The paths are relative to the plugin 'lib' directory.
    */
  @Attribute("classpath")
  def getClasspath: String = myClasspath
  def setClasspath(classpath: String): Unit = myClasspath = classpath

  @Transient
  def getPluginDescriptor: PluginDescriptor = myPluginDescriptor
  @Transient
  final def classpathSeq: Seq[String] = getClasspath.split(";").filter(StringUtils.isNotBlank).toSeq

  override def setPluginDescriptor(pluginDescriptor: PluginDescriptor): Unit =
    myPluginDescriptor = pluginDescriptor
}

object CompileServerClasspathProvider
  extends ExtensionPointDeclaration[CompileServerClasspathProvider](
    "org.intellij.scala.compileServerClasspathProvider"
  )