package org.jetbrains.plugins.scala
package util

import java.net.URL
import javax.swing.event.HyperlinkEvent

import com.intellij.notification._
import com.intellij.openapi.project.Project

/**
 * User: Dmitry Naydanov
 * Date: 11/27/13
 */
object NotificationUtil  {
  def builder(project: Project, message: String) = new NotificationBuilder(project, message)
  
  class NotificationBuilder protected[NotificationUtil] (project: Project, message: String) {
    private var group: String = "scala"
    private var title: String = "Warning"
    private var notificationType: NotificationType = NotificationType.WARNING
    private var displayType: NotificationDisplayType = NotificationDisplayType.BALLOON
    private var handler: Handler = IdHandler
    
    def setGroup(group: String): NotificationBuilder = {this.group = group; this}
    def setTitle(title: String): NotificationBuilder = {this.title = title; this}
    def setNotificationType(notificationType: NotificationType): NotificationBuilder = {this.notificationType = notificationType; this}
    def setDisplayType(displayType: NotificationDisplayType): NotificationBuilder = {this.displayType = displayType; this}
    def setHandler(handler: Handler): NotificationBuilder = {this.handler = handler; this}
    
    def notification = new Notification(group, title, message, notificationType, new HyperlinkListener(handler))
    def show(): Unit = Notifications.Bus.notify(notification, project)
  }
  
  def showMessage(project: Project, message: String, 
             group: String = "scala", 
             title: String = "Warning", 
             notificationType: NotificationType = NotificationType.WARNING,
             displayType: NotificationDisplayType = NotificationDisplayType.BALLOON,
             handler: Handler = IdHandler) {
    
    builder(project, message).
      setGroup(group).
      setTitle(title).
      setNotificationType(notificationType).
      setDisplayType(displayType).
      setHandler(handler).show()
  }
  
  type Handler = (String) => (Unit)
  
  private val IdHandler: Handler = { (_: String) => {} }
  
  private class HyperlinkListener(handler: Handler) extends NotificationListener {
    def hyperlinkUpdate(notification: Notification, event: HyperlinkEvent) {
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
      case "http" => event.getURL
    }
  }

  protected[NotificationUtil] object Action {
    def unapply(event: HyperlinkEvent): Option[String] = Option(event.getURL) map (_.getProtocol) collect {
      case "ftp" => event.getURL.getHost
    }
  }
}
