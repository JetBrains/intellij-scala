package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, ToggleAction}

class ShowImplicitHintsAction extends ToggleAction {
  override def isSelected(event: AnActionEvent): Boolean =
    ImplicitHints.enabled

  override def setSelected(event: AnActionEvent, value: Boolean): Unit = {
    ImplicitHints.enabled = value
  }
}

object ShowImplicitHintsAction {
  class Enable extends AnAction {
    override def actionPerformed(e: AnActionEvent): Unit = {
      ImplicitHints.enabled = true
    }
  }

  class Disable extends AnAction {
    override def actionPerformed(e: AnActionEvent): Unit = {
      ImplicitHints.enabled = false
    }
  }
}
