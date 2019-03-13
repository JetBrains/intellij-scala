package org.jetbrains.bsp

import com.intellij.notification.NotificationGroup
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.util.registry.Registry
import javax.swing.Icon

object BSP {
  val Name = "bsp"
  val Icon: Icon = Icons.BSP

  val ProjectSystemId = new ProjectSystemId("BSP", Name)

  val balloonNotification: NotificationGroup = NotificationGroup.balloonGroup("bsp")
}
