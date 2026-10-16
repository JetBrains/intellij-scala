package org.jetbrains.plugins.scala.extensions

import com.intellij.openapi.project.{DumbService, Project}
import org.jetbrains.plugins.scala.internal.ScalaDynamicPluginManager

def invokeWhenSmart(project: Project)(body: => Unit): Unit = {
  // We need to check for unloading here, because when the scala plugin tries to unload,
  // it is not in smart mode, which means tasks might get scheduled but will not be executed until after
  // the plugin is unloaded, which will fail, because the scheduled tasks prevent it from doing so.
  if (!ScalaDynamicPluginManager.isScalaPluginUnloading) {
    DumbService.getInstance(project).runWhenSmart(() => body)
  }
}
