package org.jetbrains.plugins.scala.actions

import com.intellij.openapi.actionSystem._
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.util.FileContentUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.actions.ToggleTypeAwareHighlightingAction.toggleSettingAndRehighlight
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.jdk.CollectionConverters._

class ToggleTypeAwareHighlightingAction extends AnAction(
  ScalaBundle.message("toggle.type.aware.highlighting.menu.action.text"),
  ScalaBundle.message("toggle.type.aware.highlighting.menu.action.description"),
  /* icon = */ null
) {
  override def actionPerformed(e: AnActionEvent): Unit = {
    CommonDataKeys.PROJECT.getData(e.getDataContext) match {
      case project: Project =>
        toggleSettingAndRehighlight(project)
      case _ =>
    }
  }
}

object ToggleTypeAwareHighlightingAction {
  def toggleSettingAndRehighlight(project: Project): Unit = {
    val settings = ScalaProjectSettings.getInstance(project)
    settings.toggleTypeAwareHighlighting()
    invokeLater(ModalityState.NON_MODAL) {
      reparseActiveFiles(project)
    }
  }

  private def reparseActiveFiles(project: Project): Unit = {
    val openEditors = EditorFactory.getInstance().getAllEditors.toSeq.filterByType[EditorEx]
    val vFiles = openEditors.map(_.getVirtualFile).filterNot(_ == null)
    FileContentUtil.reparseFiles(project, vFiles.asJava, true)
  }
}