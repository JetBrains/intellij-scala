package org.jetbrains.plugins.scala.actions

import com.intellij.openapi.actionSystem._
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.actions.ScalaHighlightingModeAction.perform
import org.jetbrains.plugins.scala.settings.ShowSettingsUtilImplExt
import org.jetbrains.plugins.scala.settings.sections.EditorSettingsSectionConfigurable

final class ScalaHighlightingModeAction extends AnAction(
  ScalaBundle.message("scala.highlighting.mode.action.text"),
  ScalaBundle.message("scala.highlighting.mode.action.description"),
  /* icon = */ null
) {
  override def actionPerformed(e: AnActionEvent): Unit = {
    CommonDataKeys.PROJECT.getData(e.getDataContext) match {
      case project: Project => perform(project)
      case _ =>
    }
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT
}

object ScalaHighlightingModeAction {
  def perform(project: Project): Unit =
    ShowSettingsUtilImplExt.showSettingsDialog(project, classOf[EditorSettingsSectionConfigurable], filter = "")
}
