package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnActionEvent, CommonDataKeys, ToggleAction}
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle

class XRayModeAction extends ToggleAction(() => ScalaCodeInsightBundle.message("action.xray.mode"), AllIcons.Actions.Show) {
  override def isSelected(e: AnActionEvent): Boolean =
    ScalaHintsSettings.xRayMode && ScalaHintsSettings.xRayModePinned

  override def setSelected(e: AnActionEvent, state: Boolean): Unit = {
    CommonDataKeys.EDITOR.getData(e.getDataContext) match {
      case editor: Editor =>
        ScalaEditorFactoryListener.setXRayModeEnabled(state, editor)
      case _ =>
        ScalaHintsSettings.xRayMode = state
    }
    ScalaHintsSettings.xRayModePinned = state
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.EDT
}
