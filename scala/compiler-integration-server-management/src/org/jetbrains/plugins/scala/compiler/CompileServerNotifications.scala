package org.jetbrains.plugins.scala.compiler

import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon.Position
import com.intellij.openapi.ui.popup.{Balloon, JBPopupFactory}
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.PositionTracker
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.NlsString

import java.awt.Point

private object CompileServerNotifications {
  /**
   * This method only shows the balloon, but doesn't log it in the "Event Log" tool window.
   *
   * Current IntelliJ API doesn't support showing real Notifications on widgets (like on tool windows)
   * and you can add a "Event Log" entry only via a `com.intellij.notification.Notification` object.
   *
   * TODO: rewrite with proper api when IDEA-273990 is fixed
   */
  private def showBalloonNotificationOnWidget(message: NlsString, project: Project): Unit = {
    val balloonBuilder = JBPopupFactory.getInstance.createHtmlTextBalloonBuilder(message.nls, MessageType.INFO, null)
    val balloon = balloonBuilder.createBalloon()

    val statusBar = Option(WindowManager.getInstance.getStatusBar(project))
    val positionTracker = statusBar match {
      case Some(bar: IdeStatusBarImpl) if bar.getComponent.isVisible =>
        val component = Option(bar.getWidgetComponent(CompileServerWidgetFactory.ID))
        component.map { c =>
          new PositionTracker[Balloon](c) {
            override def recalculateLocation(b: Balloon): RelativePoint =
              new RelativePoint(c, new Point(c.getWidth / 2, 0))
          }
        }
      case _ =>
        // if status bar is not visible, show the balloon in the right-bottom corner
        val component = Option(WindowManager.getInstance.getIdeFrame(project)).map(_.getComponent)
        component.map { c =>
          new PositionTracker[Balloon](c) {
            override def recalculateLocation(b: Balloon): RelativePoint = {
              new RelativePoint(c, new Point(c.getWidth, c.getHeight))
            }
          }
        }
    }

    positionTracker.foreach(balloon.show(_, Position.above))
  }

  @Nls
  private def title: String = ServerManagementBundle.message("scala.compile.server.title")

  private val NotificationGroupId = "Scala Compile Server"

  def showNotification(@Nls message: String, notificationType: NotificationType, project: Option[Project]): Unit = {
    Notifications.Bus.notify(new Notification(NotificationGroupId, title, message, notificationType), project.orNull)
  }

  @RequiresEdt
  def showStoppedByIdleTimeoutNotification(project: Project): Unit = {
    val message = NlsString(ServerManagementBundle.message("compile.server.stopped.due.to.inactivity"))
    showBalloonNotificationOnWidget(message, project)
  }
}
