package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.notification._
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.util.{ScalaCollectionsUtil, ScalaNotificationGroups}

import scala.annotation.nowarn
import scala.collection.mutable
import scala.ref.WeakReference

private[formatting]
object ScalafmtNotifications {

  private val ScalafmtInfoBalloonGroupId        = "Scalafmt Notifications"
  private val ScalafmtErrorBalloonGroupId       = "Scalafmt Error Notifications"
  private val ScalafmtFormatErrorBalloonGroupId = "Scalafmt Format Error Notifications"

  private def scalafmtInfoBalloonGroup        = NotificationGroupManager.getInstance().getNotificationGroup(ScalafmtInfoBalloonGroupId)
  private def scalafmtErrorBalloonGroup       = NotificationGroupManager.getInstance().getNotificationGroup(ScalafmtErrorBalloonGroupId)
  private def scalafmtFormatErrorBalloonGroup = NotificationGroupManager.getInstance().getNotificationGroup(ScalafmtFormatErrorBalloonGroupId)

  // do not display notification with same content several times
  private val messagesShown: mutable.Map[String, WeakReference[Notification]] = ScalaCollectionsUtil.newConcurrentMap

  private def displayNotification(
    message: String,
    notificationType: NotificationType,
    group: NotificationGroup,
    actions: Seq[NotificationAction] = Nil,
    listener: Option[NotificationListener] = None
  )
    (implicit project: Project): Unit = {
    updateShownMessagesCache(message)
    if (messagesShown.contains(message))
      return

    val notification = group.createNotification(message, notificationType)
    listener.foreach(notification.setListener): @nowarn("cat=deprecation")
    actions.foreach(notification.addAction)
    notification.notify(project)

    messagesShown(message) = WeakReference(notification)
    notification.whenExpired(() => messagesShown.remove(message))
  }

  // notification.getBalloon can be null right after notification creation
  // so we can detect notification balloon close event only this way
  private def updateShownMessagesCache(message: String): Unit = {
    messagesShown.get(message) match {
      case Some(WeakReference(notification)) =>
        val balloon = notification.getBalloon
        if (balloon == null || balloon.isDisposed) {
          messagesShown.remove(message)
        }
      case _ =>
        messagesShown.remove(message)
    }
  }

  def displayInfo(
    message: String,
    actions: Seq[NotificationAction] = Nil
  )(implicit project: Project): Unit = {
    displayNotification(message, NotificationType.INFORMATION, scalafmtInfoBalloonGroup, actions)
  }

  def displayWarning(
    message: String,
    actions: Seq[NotificationAction] = Nil
  )(implicit project: Project): Unit = {
    displayNotification(message, NotificationType.WARNING, scalafmtInfoBalloonGroup, actions)
  }

  def displayError(
    message: String,
    actions: Seq[NotificationAction] = Nil
  )(implicit project: Project = null): Unit = {
    displayNotification(message, NotificationType.ERROR, scalafmtErrorBalloonGroup, actions)
  }

  def displayFormatError(message: String,
                         actions: Seq[NotificationAction] = Nil,
                         listener: Option[NotificationListener] = None)
                  (implicit project: Project = null): Unit = {
    displayNotification(message, NotificationType.ERROR, scalafmtFormatErrorBalloonGroup, actions, listener)
  }

  def hideAllFormatErrorNotifications(): Unit = {
    for {
      notificationRef <- messagesShown.valuesIterator
      notification <- notificationRef.get
      if notification.getGroupId == ScalafmtFormatErrorBalloonGroupId
    } notification.hideBalloon()
  }

  sealed trait FmtVerbosity

  object FmtVerbosity {
    object Silent extends FmtVerbosity // do not show any notifications
    object FailSilent extends FmtVerbosity // show only info notifications
    object Verbose extends FmtVerbosity // show all notifications
  }
}
