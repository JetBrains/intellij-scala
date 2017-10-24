package org.jetbrains.plugins.hydra.actions

import com.intellij.openapi.actionSystem.{AnActionEvent, CommonDataKeys, ToggleAction}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.hydra.compiler.HydraCompilerSettings

/**
  * @author Maris Alexandru
  */
class EnableHydraSettingsAction extends ToggleAction{

  override def setSelected(e: AnActionEvent, toSet: Boolean): Unit = {
    CommonDataKeys.PROJECT.getData(e.getDataContext) match {
      case project: Project => {
        val settings = HydraCompilerSettings.getInstance(project)
        settings.isHydraSettingsEnabled = toSet
      }
      case _ =>
    }
  }

  override def isSelected(e: AnActionEvent): Boolean = {
    CommonDataKeys.PROJECT.getData(e.getDataContext) match {
      case project: Project => {
        val settings = HydraCompilerSettings.getInstance(project)
        settings.isHydraSettingsEnabled
      }
      case _ => false
    }
  }
}
