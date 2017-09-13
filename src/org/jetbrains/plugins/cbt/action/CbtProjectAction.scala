package org.jetbrains.plugins.cbt.action

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys, Presentation}
import org.jetbrains.plugins.cbt._
import org.jetbrains.plugins.cbt.project.settings.CbtProjectSettings

trait CbtProjectAction extends AnAction {
  override def update(e: AnActionEvent): Unit = {
    val presentation = e.getPresentation
    val dataContext = e.getDataContext
    val project = CommonDataKeys.PROJECT.getData(dataContext)

    if (project.isCbtProject && enabled(e)) {
      enable(presentation)
    } else {
      disable(presentation)
    }
  }

  protected def enable(presentation: Presentation): Unit = {
    presentation.setEnabled(true)
    presentation.setVisible(true)
  }

  protected def disable(presentation: Presentation): Unit = {
    presentation.setEnabled(false)
    presentation.setVisible(false)
  }

  protected def isModule(settings: CbtProjectSettings, path: String): Boolean =
    settings.extraModules.contains(path)

  def enabled(e: AnActionEvent): Boolean
}
