package org.jetbrains.plugins.scala

import com.intellij.notification.{Notification, NotificationListener, NotificationType, Notifications}

import java.awt.datatransfer.StringSelection
import java.awt.{Desktop, Toolkit}
import java.net.URI
import javax.swing.event.HyperlinkEvent
import scala.annotation.nowarn

object DesktopUtils {

  def browse(url: String): Unit = {
    val supported = Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE)

    if(supported)
      Desktop.getDesktop.browse(new URI(url))
    else {
      val notification = new Notification(
        "scala",
        ScalaBundle.message("title.problem.opening.web.page"),
        ScalaBundle.message("html.unable.to.launch.web.browser", url),
        NotificationType.WARNING
      )
      notification.setListener(Listener): @nowarn("cat=deprecation")
      Notifications.Bus.notify(notification)
    }
  }

   private object Listener extends NotificationListener.Adapter {
    override def hyperlinkActivated(notification: Notification, event: HyperlinkEvent): Unit = {
      Option(event.getURL).foreach { url =>
         val clipboard = Toolkit.getDefaultToolkit.getSystemClipboard
         clipboard.setContents(new StringSelection(url.toExternalForm), null)
      }
    }
  }
}