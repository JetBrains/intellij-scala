package org.jetbrains.plugins.scala
package util

import java.net.URL

import com.intellij.notification._
import com.intellij.openapi.project.Project
import javax.swing.event.HyperlinkEvent
import org.jetbrains.annotations.{Nls, NonNls}
import org.jetbrains.plugins.scala.ScalaBundle

import scala.annotation.nowarn
import scala.collection.mutable

/**
 * User: Dmitry Naydanov
 * Date: 11/27/13
 */
// TODO Why do we need a mutable builder when we have named / default arguments?
object NotificationUtil  {
  def builder(project: Project, @Nls message: String) = new NotificationBuilder(project, message)

  class NotificationBuilder protected[NotificationUtil] (project: Project, @Nls message: String) {
    private var group: String = "Scala"
    @Nls
    private var title: Option[String] = None
    private var notificationType: NotificationType = NotificationType.WARNING
    private var displayType: NotificationDisplayType = NotificationDisplayType.BALLOON // TODO Why it's present but not applied?
    private var handler: Handler = IdHandler
    private val actions: mutable.Buffer[NotificationAction] = mutable.Buffer()

    def setGroup(@NonNls group: String): this.type = {this.group = group; this}
    def setTitle(@Nls title: String): this.type = {this.title = Some(title); this}
    def removeTitle(): this.type = {this.title = None; this}
    def setNotificationType(notificationType: NotificationType): this.type = {this.notificationType = notificationType; this}
    // @deprecated TODO: yeah! but why? and replace with what?
    def setDisplayType(displayType: NotificationDisplayType): this.type = {this.displayType = displayType; this}
    def setHandler(handler: Handler): this.type = {this.handler = handler; this}
    def addAction(action: NotificationAction): this.type = {actions += action; this}

    def show(): Unit = {
      val notification = title match {
        case Some(t) => new Notification(group, t, message, notificationType)
        case None    => new Notification(group, message, notificationType)
      }
      notification.setListener(new HyperlinkListener(handler))
      actions.foreach(notification.addAction)

      Notifications.Bus.notify(notification, project)
    }
  }

  def showMessage(project: Project,
                  @Nls message: String,
                  group: String = "scala",
                  @Nls title: String = ScalaBundle.message("default.notification.title"),
                  notificationType: NotificationType = NotificationType.WARNING,
                  displayType: NotificationDisplayType = NotificationDisplayType.BALLOON,
                  handler: Handler = IdHandler): Unit = {

    builder(project, message).
      setGroup(group).
      setTitle(title).
      setNotificationType(notificationType).
      setDisplayType(displayType).
      setHandler(handler).show()
  }

  type Handler = String => Unit

  private val IdHandler: Handler = _ => ()

  class HyperlinkListener(handler: Handler = IdHandler) extends NotificationListener {
    override def hyperlinkUpdate(notification: Notification, event: HyperlinkEvent): Unit = {
      event match {
        case Link(url) => DesktopUtils browse url
        case Action(command) =>
          notification.expire()
          handler(command)
        case _ =>
      }
    }
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
