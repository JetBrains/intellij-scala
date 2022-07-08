package org.jetbrains.plugins.scala
package util

import com.intellij.notification._
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.{Nls, NonNls}

import java.net.URL
import javax.swing.event.HyperlinkEvent
import scala.collection.mutable

// TODO Why do we need a mutable builder when we have named / default arguments?
object NotificationUtil  {
  def builder(project: Project, @Nls message: String) = new NotificationBuilder(project, message)

  class NotificationBuilder protected[NotificationUtil] (project: Project, @Nls message: String) {
    private var group: String = "Scala"
    @Nls
    private var title: Option[String] = None
    private var notificationType: NotificationType = NotificationType.WARNING
    private var displayType: NotificationDisplayType = NotificationDisplayType.BALLOON // TODO Why it's present but not applied?
    private val actions: mutable.Buffer[NotificationAction] = mutable.Buffer()

    def setGroup(@NonNls group: String): this.type = {this.group = group; this}
    def setTitle(@Nls title: String): this.type = {this.title = Some(title); this}

    def setNotificationType(notificationType: NotificationType): this.type = {this.notificationType = notificationType; this}
    // @deprecated TODO: yeah! but why? and replace with what?
    def setDisplayType(displayType: NotificationDisplayType): this.type = {this.displayType = displayType; this}
    def addAction(action: NotificationAction): this.type = {actions += action; this}

    def show(): Unit = {
      val notification = build()
      Notifications.Bus.notify(notification, project)
    }

    def build(): Notification = {
      val notification = title match {
        case Some(t) => new Notification(group, t, message, notificationType)
        case None    => new Notification(group, message, notificationType)
      }
      actions.foreach(notification.addAction)
      notification
    }
  }

  def showMessage(
    project: Project,
    @Nls message: String,
    group: String = "scala",
    @Nls title: String = ScalaBundle.message("default.notification.title"),
    notificationType: NotificationType = NotificationType.WARNING,
    displayType: NotificationDisplayType = NotificationDisplayType.BALLOON,
  ): Unit = {
    val notificationBuilder = builder(project, message)
      .setGroup(group)
      .setTitle(title)
      .setNotificationType(notificationType)
      .setDisplayType(displayType)
    notificationBuilder.show()
  }

  protected[NotificationUtil] object Link {
    def unapply(event: HyperlinkEvent): Option[URL] = Option(event.getURL) map (_.getProtocol) collect {
      case "http" | "https" => event.getURL
    }
  }

  protected[NotificationUtil] object Action {
    def unapply(event: HyperlinkEvent): Option[String] = Option(event.getURL) map (_.getProtocol) collect {
      case "ftp" => event.getURL.getHost
    }
  }
}
