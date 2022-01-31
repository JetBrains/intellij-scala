package org.jetbrains.plugins.scala.components.libextensions.ui

import com.intellij.notification.{Notification, NotificationType, Notifications}

import javax.swing.event.HyperlinkEvent
import org.jetbrains.plugins.scala.ScalaBundle

import scala.annotation.nowarn

class PopupHelper {
  import PopupHelper._
  def showEnablePopup(yesCallback: () => Unit, noCallback: () => Unit): Unit = {
    val notification = new Notification(GROUP_ID, ScalaBundle.message("title.extensions.available"),
      ScalaBundle.message("additional.support.has.been.found.popup"),
      NotificationType.INFORMATION
    )
    notification.setListener(
      (notification: Notification, event: HyperlinkEvent) => {
        notification.expire()
        event.getDescription match {
          case "Yes" => yesCallback()
          case "No"  => noCallback()
        }
      }
    ): @nowarn("cat=deprecation")
    Notifications.Bus.notify(notification)
  }
}

object PopupHelper {
  val GROUP_ID = "Scala Library Extension"
}