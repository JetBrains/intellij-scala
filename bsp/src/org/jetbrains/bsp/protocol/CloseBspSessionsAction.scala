package org.jetbrains.bsp.protocol

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class CloseBspSessionsAction extends DumbAwareAction {
  override def actionPerformed(e: AnActionEvent): Unit = {
    BspCommunicationService.getInstance.closeAll
  }
}
