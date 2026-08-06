package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.settings.{ScalaCompileServerSettings, ShowSettingsUtilImplExt}

object CompileServerSettingsUtil {
  def showCompileServerSettingsDialog(project: Project, filter: String = ""): Unit =
    ShowSettingsUtilImplExt.showSettingsDialog(project, classOf[ScalaCompileServerForm], filter)

  def enableCompileServer(): Unit = {
    val settings = ScalaCompileServerSettings.getInstance()
    settings.COMPILE_SERVER_ENABLED = true
  }
}
