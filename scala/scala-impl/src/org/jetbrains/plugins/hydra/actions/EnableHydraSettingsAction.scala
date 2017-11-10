package org.jetbrains.plugins.hydra.actions

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.hydra.compiler.{HydraCompilerSettings, HydraCompilerSettingsManager}

/**
  * @author Maris Alexandru
  */
class EnableHydraSettingsAction extends AnAction{
  def actionPerformed(e: AnActionEvent): Unit = {
    CommonDataKeys.PROJECT.getData(e.getDataContext) match {
      case project: Project => {
        val settings = HydraCompilerSettings.getInstance(project)
        HydraCompilerSettingsManager.showHydraCompileSettingsDialog(project)
        settings.isHydraSettingsEnabled = true
      }
      case _ =>
    }
  }
}
