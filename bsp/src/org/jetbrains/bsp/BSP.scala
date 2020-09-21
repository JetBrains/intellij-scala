package org.jetbrains.bsp

import com.intellij.notification.NotificationGroup
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import javax.swing.Icon
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups

object BSP {
  @Nls
  //noinspection ScalaExtractStringToBundle
  val Name = "BSP"
  val Icon: Icon = Icons.BSP

  val ProjectSystemId = new ProjectSystemId("BSP", Name)

  val balloonNotification: NotificationGroup = ScalaNotificationGroups.balloonGroup
}
