package org.jetbrains.plugins.scala.compiler.polyglot

import com.intellij.openapi.project.Project

private object KotlinDaemonUtil {
  def disableKotlinDaemon(project: Project): Unit = {
    try {
      val cls = Class.forName("org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerWorkspaceSettings")
      val getInstanceMethod = cls.getMethod("getInstance", classOf[Project])
      getInstanceMethod.setAccessible(true)
      val settings = getInstanceMethod.invoke(null, project)
      val setter = cls.getMethod("setEnableDaemon", classOf[Boolean])
      setter.setAccessible(true)
      setter.invoke(settings, false)
    } catch {
      case _: Exception =>
    }
  }
}
