package org.jetbrains.plugins.cbt.action

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys, Presentation}
import org.jetbrains.plugins.cbt._

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

  protected def enable(presentation: Presentation) {
    presentation.setEnabled(true)
    presentation.setVisible(true)
  }

  protected def disable(presentation: Presentation) {
    presentation.setEnabled(false)
    presentation.setVisible(false)
  }

  def enabled(e: AnActionEvent): Boolean
}
