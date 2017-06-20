package org.jetbrains.plugins.cbt.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.action.ExternalSystemAction

class RefreshCbtProjectAction extends ExternalSystemAction {
  setText("Refresh CBT project")
  override def actionPerformed(e: AnActionEvent): Unit = {}
}
