package org.jetbrains.plugins.scala.actions

import com.intellij.openapi.actionSystem._
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.components.HighlightingAdvisor

/**
 * User: Alexander Podkhalyuzin
 * Date: 27.01.2010
 */

class ToggleTypeAwareHighlightingAction extends AnAction {
  def actionPerformed(e: AnActionEvent) {
    CommonDataKeys.PROJECT.getData(e.getDataContext) match {
      case project: Project => HighlightingAdvisor.getInstance(project).toggle()
      case _ =>
    }
  }
}