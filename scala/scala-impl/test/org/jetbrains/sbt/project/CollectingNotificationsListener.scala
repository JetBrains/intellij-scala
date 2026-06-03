package org.jetbrains.sbt.project

import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.project.Project

import scala.collection.mutable

//TODO: move it to some base class and call assertNoNotificationsShown in more tests (BSP/Maven/Gradle/new project wizard)
class CollectingNotificationsListener(types: Set[NotificationType]) extends Notifications {
  private val notifications = mutable.ArrayBuffer[Notification]()

  def getNotifications: Seq[Notification] = notifications.toSeq

  override def notify(notification: Notification): Unit = {
    if (types.contains(notification.getType)) {
      notifications += notification
    }
  }
}

object CollectingNotificationsListener {

  def subscribeOnWarningsAndErrors(project: Project): CollectingNotificationsListener = {
    val notificationsCollector = new CollectingNotificationsListener(Set(
      NotificationType.WARNING,
      NotificationType.ERROR
    ))
    project.getMessageBus.connect().subscribe(Notifications.TOPIC, notificationsCollector)
    notificationsCollector
  }
}