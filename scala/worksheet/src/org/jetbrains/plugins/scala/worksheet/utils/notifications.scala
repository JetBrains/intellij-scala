package org.jetbrains.plugins.scala.worksheet.utils

import com.intellij.notification.{NotificationGroup, NotificationGroupManager}

object notifications {

  /** Also see [[org.jetbrains.plugins.scala.util.ScalaNotificationGroups]] */
  def WorksheetNotificationsGroup: NotificationGroup =
    NotificationGroupManager.getInstance().getNotificationGroup("scala.worksheet")

}
