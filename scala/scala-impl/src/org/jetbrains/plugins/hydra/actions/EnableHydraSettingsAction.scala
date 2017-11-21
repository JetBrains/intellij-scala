package org.jetbrains.plugins.hydra.actions

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.hydra.compiler.HydraCompilerSettingsManager
import org.jetbrains.plugins.hydra.settings.HydraApplicationSettings

/**
  * @author Maris Alexandru
  */
class EnableHydraSettingsAction extends AnAction {
  def actionPerformed(e: AnActionEvent): Unit = {
    CommonDataKeys.PROJECT.getData(e.getDataContext) match {
      case project: Project =>
        val settings = HydraApplicationSettings.getInstance()
        settings.isHydraSettingsEnabled = true
        HydraCompilerSettingsManager.showHydraCompileSettingsDialog(project)
      case _ =>
    }
  }
}
