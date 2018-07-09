package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.actionSystem._

class ShowImplicitHintsAction extends ToggleAction {
  setShortcuts(ShowImplicitHintsAction.Id, EnableShortcuts)

  override def isSelected(event: AnActionEvent): Boolean = ImplicitHints.enabled

  override def setSelected(e: AnActionEvent, state: Boolean): Unit = {
    ImplicitHints.enabled = state
    ImplicitHints.updateInAllEditors()
    if (!state) {
      MouseHandler.removeEscKeyListeners()
    }
  }
}

private object ShowImplicitHintsAction {
  val Id = "Scala.ShowImplicits"
}
