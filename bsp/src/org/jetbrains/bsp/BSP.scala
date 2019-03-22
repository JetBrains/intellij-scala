package org.jetbrains.bsp

import com.intellij.notification.NotificationGroup
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.util.registry.Registry
import javax.swing.Icon

object BSP {
  val Name = "bsp"
  val Icon: Icon = Icons.BSP

  val ProjectSystemId = new ProjectSystemId("BSP", Name)

  val RegistryKeyFeatureEnabled: String = BSP.ProjectSystemId + ".experimental.feature.enabled"

  val balloonNotification: NotificationGroup = NotificationGroup.balloonGroup("bsp")

  def enabled: Boolean =
    Registry.get(RegistryKeyFeatureEnabled).asBoolean()
}
