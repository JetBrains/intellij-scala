package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}

// See PresentationRenderer.getContextMenuGroupId
class DummyAction extends AnAction {
  override def actionPerformed(e: AnActionEvent): Unit = {}
}
