package org.jetbrains.plugins.scala.components.libextensions.ui

import com.intellij.notification.{Notification, NotificationType, Notifications}
import javax.swing.event.HyperlinkEvent

class PopupHelper {
  import PopupHelper._
  def showEnablePopup(yesCallback: () => Unit, noCallback: () => Unit): Unit = {
    val notification = new Notification(GROUP_ID, "Extensions available",
      s"""<p>Additional support has been found for some of your libraries.</p>
         |<p>Do you want to enable it? <a href="Yes">Yes</a> / <a href="No">No</a></p>
       """.stripMargin,
      NotificationType.INFORMATION, (notification: Notification, event: HyperlinkEvent) => {
        notification.expire()
        event.getDescription match {
          case "Yes" => yesCallback()
          case "No"  => noCallback()
        }
      }
    )
    Notifications.Bus.notify(notification)
  }
}

object PopupHelper {
  val GROUP_ID      = "Scala Library Extension"
}