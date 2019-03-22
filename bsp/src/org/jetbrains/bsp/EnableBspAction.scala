package org.jetbrains.bsp

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.registry.Registry

class EnableBspAction extends AnAction {
  def actionPerformed(e: AnActionEvent): Unit = {
    Registry
      .get(BSP.RegistryKeyFeatureEnabled)
      .setValue(true)

    BSP.balloonNotification
      .createNotification(
        "enabled experimental bsp (build server protocol) support",
        MessageType.INFO
      )
      .notify(e.getProject)
  }
}
