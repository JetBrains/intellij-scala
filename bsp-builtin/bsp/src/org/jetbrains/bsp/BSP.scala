package org.jetbrains.bsp

import com.intellij.notification.{NotificationGroup, NotificationGroupManager}
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.jetbrains.annotations.Nls

import javax.swing.Icon

object BSP {
  @Nls
  //noinspection ScalaExtractStringToBundle
  val Name = "BSP"
  val Icon: Icon = Icons.BSP

  val ProjectSystemId = new ProjectSystemId("BSP", Name)

  lazy val NotificationGroup: NotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("BSP")
}
