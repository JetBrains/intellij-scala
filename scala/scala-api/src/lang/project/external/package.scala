package org.jetbrains.plugins.scala.project

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.notification.NotificationData
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.TestOnly

package object external {

  @TestOnly
  case class ShownNotification(id: ProjectSystemId, data: NotificationData)

  @TestOnly
  val ShownNotificationsKey: Key[Seq[ShownNotification]] = Key.create("shown.notifications")
}
