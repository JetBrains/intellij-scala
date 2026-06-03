package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.actionSystem._
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle

class ShowImplicitHintsAction extends ToggleAction(
  ScalaCodeInsightBundle.message("show.implicit.hints.action.text"),
  ScalaCodeInsightBundle.message("show.implicit.hints.action.description"),
  /* icon = */ null
) {

  override def isSelected(event: AnActionEvent): Boolean = ImplicitHints.enabled

  override def setSelected(e: AnActionEvent, state: Boolean): Unit = {
    ImplicitHints.enabled = state
    ImplicitHints.updateInAllEditors()
    if (!state) {
      MouseHandler.removeEscKeyListeners()
    }
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.EDT
}

private object ShowImplicitHintsAction {
  val Id = "Scala.ShowImplicits"
}
