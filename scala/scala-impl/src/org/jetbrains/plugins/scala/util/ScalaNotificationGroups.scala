package org.jetbrains.plugins.scala.util

import com.intellij.notification.{NotificationGroup, NotificationGroupManager}

/**
 * Helper methods to replace deprecated [[com.intellij.notification.NotificationGroup]] static methods.
 *
 * Uses groups registered as EPs instead of dynamically created. IDs must correspond to ones declared in `scala-plugin-common.xml`
 */
object ScalaNotificationGroups {
  private val BALLOON_GROUP_ID        = "Scala Balloon Notifications"
  private val TOOLWINDOW_GROUP_ID     = "Scala ToolWindow Notifications"
  private val STICKY_BALLOON_GROUP_ID = "Persistent Scala Notifications"

  def toolWindowGroup: NotificationGroup =
    NotificationGroupManager.getInstance().getNotificationGroup(TOOLWINDOW_GROUP_ID)

  def balloonGroup: NotificationGroup =
    NotificationGroupManager.getInstance().getNotificationGroup(BALLOON_GROUP_ID)

  def stickyBalloonGroup: NotificationGroup =
    NotificationGroupManager.getInstance().getNotificationGroup(STICKY_BALLOON_GROUP_ID)
}
