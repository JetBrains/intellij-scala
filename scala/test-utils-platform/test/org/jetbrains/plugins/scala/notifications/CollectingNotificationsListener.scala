package org.jetbrains.plugins.scala.notifications

import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.junit.Assert.fail

import scala.collection.mutable

/**
 * Test helper that subscribes to [[com.intellij.notification.Notifications#TOPIC]]
 * and collects notifications of the configured types
 *
 * Also consider using [[com.intellij.notification.NotificationsManager#getNotificationsOfType]].
 * It is populated before projects open, so it can include startup notifications
 * (for example, from a `postStartupActivity`) that this listener may miss because it subscribes only after a `Project` is available.
 * See `LegacySbtVersionProjectNotificationTest` for example
 */
class CollectingNotificationsListener(types: Set[NotificationType]) extends Notifications {
  private val notifications = mutable.ArrayBuffer[Notification]()

  def getNotifications: Seq[Notification] = notifications.toSeq

  override def notify(notification: Notification): Unit = {
    if (types.contains(notification.getType)) {
      notifications += notification
    }
  }

  def assertNoNotificationsShown(mutedNotificationTitles: Seq[String] = Nil): Unit =
    CollectingNotificationsListener.assertNoNotificationsShown(getNotifications, mutedNotificationTitles)
}

object CollectingNotificationsListener {

  def subscribeOnWarningsAndErrors(project: Project): CollectingNotificationsListener =
    subscribe(project, NotificationType.WARNING, NotificationType.ERROR)

  def subscribeOnAllTypes(project: Project): CollectingNotificationsListener =
    subscribe(project, NotificationType.INFORMATION, NotificationType.WARNING, NotificationType.ERROR)

  private def subscribe(project: Project, notificationTypes: NotificationType*): CollectingNotificationsListener = {
    val notificationsCollector = new CollectingNotificationsListener(notificationTypes.toSet)
    project.getMessageBus.connect(project).subscribe(Notifications.TOPIC, notificationsCollector)
    ApplicationManager.getApplication.getMessageBus.connect(project).subscribe(Notifications.TOPIC, notificationsCollector)
    notificationsCollector
  }

  /**
   * Fails the current test if any of the given notifications is not muted.
   *
   * @param mutedNotificationTitles notifications whose [[Notification#getTitle]] is in this list
   *                                are ignored. Useful for known-noisy notifications that are
   *                                acceptable during a particular import.
   */
  def assertNoNotificationsShown(
    notifications: Seq[Notification],
    mutedNotificationTitles: Seq[String] = Nil
  ): Unit = {
    val nonMuted = notifications.filterNot(n => mutedNotificationTitles.contains(n.getTitle))
    if (nonMuted.nonEmpty) {
      val rendered = nonMuted.map(renderNotification).mkString("\n")
      fail(
        s"""Expected no notifications, but the following notifications were shown:
           |$rendered""".stripMargin
      )
    }
  }

  def renderNotification(notification: Notification): String =
    s"""Group id: ${notification.getGroupId}
       |Title: ${notification.getTitle}
       |Subtitle: ${notification.getSubtitle}
       |Content: ${notification.getContent}""".stripMargin
}
