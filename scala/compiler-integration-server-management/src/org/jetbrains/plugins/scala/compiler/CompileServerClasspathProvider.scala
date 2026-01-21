package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.extensions.{PluginAware, PluginDescriptor}
import com.intellij.util.xmlb.annotations.{Attribute, Transient}
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

@deprecated(
  message = """
Unused extension point. Any registered implementations will be ignored.
If you still need to implement this extension point, please contact the Scala Plugin team on the Discord Server or
open a YouTrack issue.
The bean class is kept in place to keep binary compatibility for a while. The code will be removed in 2026.2
""",
  since = "2026.1"
)
@Deprecated(since = "2026.1", forRemoval = true)
@ApiStatus.ScheduledForRemoval(inVersion = "2026.2")
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

@deprecated(
  message = """
Unused extension point. Any registered implementations will be ignored.
If you still need to implement this extension point, please contact the Scala Plugin team on the Discord Server or
open a YouTrack issue.
The bean class is kept in place to keep binary compatibility for a while. The code will be removed in 2026.2
""",
  since = "2026.1"
)
@Deprecated(since = "2026.1", forRemoval = true)
@ApiStatus.ScheduledForRemoval(inVersion = "2026.2")
object CompileServerClasspathProvider
  extends ExtensionPointDeclaration[CompileServerClasspathProvider](
    "org.intellij.scala.compileServerClasspathProvider"
  )