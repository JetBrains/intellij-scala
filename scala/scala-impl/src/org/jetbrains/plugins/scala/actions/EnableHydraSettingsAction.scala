package org.jetbrains.plugins.scala.actions

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.HydraCompilerSettings

/**
  * @author Maris Alexandru
  */
class EnableHydraSettingsAction extends AnAction{
  def actionPerformed(e: AnActionEvent) {
    CommonDataKeys.PROJECT.getData(e.getDataContext) match {
      case project: Project => {
        val settings = HydraCompilerSettings.getInstance(project)
        settings.isHydraSettingsEnabled = !settings.isHydraSettingsEnabled
      }
      case _ =>
    }
  }
}
