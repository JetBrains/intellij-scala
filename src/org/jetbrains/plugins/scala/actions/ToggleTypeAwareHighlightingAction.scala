package org.jetbrains.plugins.scala.actions

import com.intellij.openapi.actionSystem._
import org.jetbrains.plugins.scala.components.HighlightingAdvisor
import com.intellij.openapi.project.Project

/**
 * User: Alexander Podkhalyuzin
 * Date: 27.01.2010
 */

class ToggleTypeAwareHighlightingAction extends AnAction {
  def actionPerformed(e: AnActionEvent) {
    PlatformDataKeys.PROJECT.getData(e.getDataContext) match {
      case project: Project => HighlightingAdvisor.getInstance(project).toggle()
      case _ =>
    }
  }
}