package org.jetbrains.plugins.scala.components

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.notification._
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.plugins.scala.DesktopUtils
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

// TODO Remove in IDEA 2019.x
class ImplicitHintsAdviser extends ApplicationInitializedListener {

  private var messageBusConnection: Option[MessageBusConnection] = None

  override def componentsInitialized(): Unit = {
    if (isEnabled) {
      messageBusConnection = Some {
        ApplicationManager.getApplication.getMessageBus.connect()
      }
      messageBusConnection.foreach(_.subscribe(AnActionListener.TOPIC, actionListener))
    }
  }

  private def actionListener: AnActionListener = {
    new AnActionListener() {

      def isAction(actionId: String, action: AnAction): Boolean =
        action == ActionManager.getInstance.getAction(actionId).ensuring(_ != null, actionId)

      override def afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent): Unit = {
        if (isEnabled) {
          if (isAction("Scala.ShowImplicits", action)) {
            disable()
          }
          else if (isAction("Scala.ShowImplicitConversions", action) || isAction("Scala.ShowImplicitArguments", action)) {
            suggestImplicitHints()
            disable()
          }
        }
      }
    }
  }

  private def isEnabled: Boolean = ScalaApplicationSettings.getInstance.SUGGEST_IMPLICIT_HINTS

  private def disable(): Unit = {
    ScalaApplicationSettings.getInstance.SUGGEST_IMPLICIT_HINTS = false
    messageBusConnection.foreach(_.disconnect())
    messageBusConnection = None
  }

  private def suggestImplicitHints(): Unit = {

    val message =
      "Did you know about <strong>View | Show Implicit Hints?</strong><br>" +
        "Use <strong>Ctrl + Alt + Shift + “+”/“-”</strong> to toggle the mode."

    val notification = {
      val group = new NotificationGroup("Implicit Hints tip", NotificationDisplayType.STICKY_BALLOON, false)
      group.createNotification(message, NotificationType.INFORMATION)
    }

    notification.addAction((event: AnActionEvent) => {
      disable()
      notification.hideBalloon()

      DesktopUtils.browse(
        "https://blog.jetbrains.com/scala/2018/07/25/" +
          "intellij-scala-plugin-2018-2-advanced-implicit-support-improved-patterns-autocompletion-semantic-highlighting-scalafmt-and-more/"
      )
    })

    Notifications.Bus.notify(notification)
  }
}