package org.jetbrains.plugins.scala
package codeInsight
package implicits

import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnActionEvent, CommonDataKeys, ToggleAction}

class ExpandImplicitHintsAction extends ToggleAction(
  ScalaCodeInsightBundle.message("expand.implicit.hints.action.text"),
  ScalaCodeInsightBundle.message("expand.implicit.hints.action.description"),
  /* icon = */ null
) {
  override def update(e: AnActionEvent): Unit = {
    super.update(e)
    e.getPresentation.setEnabled(ImplicitHints.enabled)
  }

  override def isSelected(e: AnActionEvent): Boolean =
    ImplicitHints.expanded

  override def setSelected(e: AnActionEvent, state: Boolean): Unit = {
    ImplicitHints.expanded = state

    val editor = e.getData(CommonDataKeys.EDITOR)

    if (editor != null) {
      if (state) {
        ImplicitHints.expandIn(editor)
      } else {
        ImplicitHints.collapseIn(editor)
      }
    } else {
      ImplicitHints.updateInAllEditors()
    }

    if (state) {
      MouseHandler.removeEscKeyListeners()
    }
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.EDT
}

object ExpandImplicitHintsAction {
  val Id = "Scala.ExpandImplicits"
}
