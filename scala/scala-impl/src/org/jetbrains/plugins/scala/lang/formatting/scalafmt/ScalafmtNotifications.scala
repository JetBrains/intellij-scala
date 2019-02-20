package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.notification._
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.{JBPopupAdapter, LightweightWindowEvent}
import org.jetbrains.plugins.scala.util.ScalaCollectionsUtil

import scala.collection.mutable

private[formatting]
object ScalafmtNotifications {
  private val notificationGroup =
    new NotificationGroup("Scalafmt (Scala plugin)", NotificationDisplayType.BALLOON, true)
  private val notificationErrorGroup =
    new NotificationGroup("Scalafmt errors (Scala plugin)", NotificationDisplayType.STICKY_BALLOON, true)

  // do not display notification with same content several times
  private val messagesShown: mutable.Set[String] = ScalaCollectionsUtil.newConcurrentSet[String]

  private def displayNotification(message: String,
                                  notificationType: NotificationType,
                                  actions: Seq[NotificationAction] = Nil,
                                  listener: Option[NotificationListener] = None)
                                 (implicit project: Project): Unit = {
    if (messagesShown.contains(message)) return

    val notification: Notification =
      if (notificationType == NotificationType.INFORMATION) {
        notificationGroup.createNotification(message, notificationType)
      } else {
        notificationErrorGroup.createNotification(message, notificationType)
      }
    listener.foreach(notification.setListener)
    actions.foreach(notification.addAction)
    notification.notify(project)

    messagesShown += message
    notification.whenExpired(() => messagesShown.remove(message))
    Option(notification.getBalloon).foreach(_.addListener(new JBPopupAdapter() {
      override def onClosed(event: LightweightWindowEvent): Unit = messagesShown.remove(message)
    }))
  }

  def displayInfo(message: String,
                  actions: Seq[NotificationAction] = Nil,
                  listener: Option[NotificationListener] = None)
                 (implicit project: Project = null): Unit = {
    displayNotification(message, NotificationType.INFORMATION, actions, listener)
  }

  def displayWarning(message: String,
                     actions: Seq[NotificationAction] = Nil,
                     listener: Option[NotificationListener] = None)
                    (implicit project: Project = null): Unit = {
    displayNotification(message, NotificationType.WARNING, actions, listener)
  }

  def displayError(message: String,
                   actions: Seq[NotificationAction] = Nil,
                   listener: Option[NotificationListener] = None)
                  (implicit project: Project = null): Unit = {
    displayNotification(message, NotificationType.ERROR, actions, listener)
  }

}
