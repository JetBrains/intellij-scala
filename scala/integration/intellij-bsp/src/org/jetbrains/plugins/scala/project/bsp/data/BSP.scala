package org.jetbrains.plugins.scala.project.bsp.data

import com.intellij.notification.{NotificationGroup, NotificationGroupManager}
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.jetbrains.annotations.Nls

object BSP {
  @Nls
  //noinspection ScalaExtractStringToBundle
  val Name = "BSP"

  val ProjectSystemId = new ProjectSystemId("BSP", Name)

  val NotificationGroup: NotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("BSP")
}
