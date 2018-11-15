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

  private var messageBusConnection: MessageBusConnection = _

  override def componentsInitialized(): Unit = {
    if (isEnabled) {
      messageBusConnection = ApplicationManager.getApplication.getMessageBus.connect()
      messageBusConnection.subscribe(AnActionListener.TOPIC, actionListener)
    }
  }

  private def dispose(): Unit = {
    messageBusConnection.disconnect()
    messageBusConnection = null
  }

  private def actionListener: AnActionListener = {
    new AnActionListener() {
      override def afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent): Unit = {
        if (isEnabled) {
          if (isAction("Scala.ShowImplicits", action)) {
            disable()
            dispose()
          }
          else if (isAction("Scala.ShowImplicitConversions", action) || isAction("Scala.ShowImplicitArguments", action)) {
            suggestImplicitHints()
            dispose()
          }
        }
      }
    }
  }

  private def isEnabled: Boolean = ScalaApplicationSettings.getInstance.SUGGEST_IMPLICIT_HINTS

  private def disable(): Unit = {
    ScalaApplicationSettings.getInstance.SUGGEST_IMPLICIT_HINTS = false
  }

  private def suggestImplicitHints(): Unit = {

    val message =
      "Did you know about Implicit Hints?<br>" +
        "Try with <strong>Ctrl + Alt + Shift + “+”</strong>"

    val notification = {
      val group = new NotificationGroup("Implicit Hints tip", NotificationDisplayType.BALLOON, false)
      group.createNotification(message, NotificationType.INFORMATION)
    }

    notification.addAction(new AnAction("Got it!") {
      override def actionPerformed(event: AnActionEvent): Unit = {
        disable()
        notification.hideBalloon()
      }
    })

    notification.addAction(new AnAction("Learn more") {
      override def actionPerformed(event: AnActionEvent): Unit = {
        disable()
        notification.hideBalloon()

        DesktopUtils.browse(
          "https://blog.jetbrains.com/scala/2018/07/25/" +
            "intellij-scala-plugin-2018-2-advanced-implicit-support-improved-patterns-autocompletion-semantic-highlighting-scalafmt-and-more/"
        )
      }
    })

    Notifications.Bus.notify(notification)
  }

  private def isAction(actionId: String, action: AnAction): Boolean =
    action == ActionManager.getInstance.getAction(actionId).ensuring(_ != null, actionId)

}