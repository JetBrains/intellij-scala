package org.jetbrains.plugins.scala

import java.awt.datatransfer.StringSelection
import java.awt.{Desktop, Toolkit}
import java.net.{URI, URL}

import javax.swing.event.HyperlinkEvent
import com.intellij.notification.{Notification, NotificationListener, NotificationType, Notifications}
import org.intellij.lang.annotations.Language

/**
 * Pavel Fatin
 */

object DesktopUtils {
  def browse(url: URL): Unit = {
    browse(url.toExternalForm)
  }

  def browse(url: String): Unit = {
    val supported = Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE)

    if(supported)
      Desktop.getDesktop.browse(new URI(url))
    else
      Notifications.Bus.notify(new Notification("scala", ScalaBundle.message("title.problem.opening.web.page"),
        ScalaBundle.message("html.unable.to.launch.web.browser", url), NotificationType.WARNING, Listener))
  }

   private object Listener extends NotificationListener.Adapter {
    override def hyperlinkActivated(notification: Notification, event: HyperlinkEvent): Unit = {
      Option(event.getURL).foreach { url =>
         val clipboard = Toolkit.getDefaultToolkit.getSystemClipboard
         clipboard.setContents(new StringSelection(url.toExternalForm), null)
      }
    }
  }

  object LinkHandler extends NotificationListener.Adapter {
    override def hyperlinkActivated(notification: Notification, e: HyperlinkEvent): Unit = {
      Option(e.getURL).foreach(browse)
    }
  }
}