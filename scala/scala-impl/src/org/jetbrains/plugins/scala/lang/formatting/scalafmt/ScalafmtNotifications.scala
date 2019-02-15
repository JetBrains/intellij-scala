package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.notification.{NotificationAction, NotificationGroup, NotificationType, Notifications}

private[formatting]
object ScalafmtNotifications {

  private val NotificationDisplayId = "Scalafmt (Scala plugin)"
  private val NotificationTitle = "Scalafmt"
  private val notificationGroup: NotificationGroup = NotificationGroup.balloonGroup(NotificationDisplayId)

  def displayNotification(message: String, notificationType: NotificationType, actions: Seq[NotificationAction] = Nil): Unit = {
    val notification = notificationGroup.createNotification(NotificationTitle, null, message, notificationType)
    actions.foreach(notification.addAction)
    Notifications.Bus.notify(notification)
  }

  def displayInfo(message: String, actions: Seq[NotificationAction] = Nil): Unit = {
    displayNotification(message, NotificationType.INFORMATION, actions)
  }

  def displayWarning(message: String, actions: Seq[NotificationAction] = Nil): Unit = {
    displayNotification(message, NotificationType.WARNING, actions)
  }

  def displayError(message: String, actions: Seq[NotificationAction] = Nil): Unit = {
    displayNotification(message, NotificationType.ERROR, actions)
  }

}
