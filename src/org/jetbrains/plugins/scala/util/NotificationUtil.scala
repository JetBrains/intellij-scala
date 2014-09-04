package org.jetbrains.plugins.scala
package util

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
    
    def setGroup(group: String) = {this.group = group; this}
    def setTitle(title: String) = {this.title = title; this}
    def setNotificationType(notificationType: NotificationType) = {this.notificationType = notificationType; this}
    def setDisplayType(displayType: NotificationDisplayType) = {this.displayType = displayType; this}
    def setHandler(handler: Handler) = {this.handler = handler; this}
    
    def notification = new Notification(group, title, message, notificationType, new HyperlinkListener(handler))
    def show() = Notifications.Bus.notify(notification, project)
    def show(notification: Notification) = Notifications.Bus.notify(notification, project)
  }
  
  def showMessage(project: Project, message: String, 
             group: String = "scala", 
             title: String = "Warning", 
             notificationType: NotificationType = NotificationType.WARNING,
             displayType: NotificationDisplayType = NotificationDisplayType.BALLOON,
             handler: Handler = IdHandler) {
    val notification = new Notification(group, title, message, notificationType, new HyperlinkListener(handler))
    Notifications.Bus.register(group, displayType)
    Notifications.Bus.notify(notification, project)
  }
  
  type Handler = (String) => (Unit)
  
  private val IdHandler: Handler = { (s: String) => {} }
  
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
    def unapply(event: HyperlinkEvent) = Option(event.getURL) map (_.getProtocol) collect {
      case "http" => event.getURL
    }
  }

  protected[NotificationUtil] object Action {
    def unapply(event: HyperlinkEvent) = Option(event.getURL) map (_.getProtocol) collect {
      case "ftp" => event.getURL.getHost
    }
  }
}
