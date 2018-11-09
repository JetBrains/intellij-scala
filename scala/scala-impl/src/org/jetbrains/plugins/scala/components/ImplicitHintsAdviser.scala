package org.jetbrains.plugins.scala.components

import java.awt.event.KeyEvent

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.MessageBusConnection
import javax.swing.KeyStroke
import org.jetbrains.plugins.scala.components.ImplicitHintsAdviser._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.NotificationUtil

// TODO Remove in IDEA 2019.x
class ImplicitHintsAdviser extends ApplicationInitializedListener {
  private var messageBusConnection: MessageBusConnection = _

  private val actionListener = new AnActionListener() {
    override def afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent): Unit = {
      val settings = ScalaApplicationSettings.getInstance

      if (settings.SUGGEST_IMPLICIT_HINTS) {
        if (isAction("Scala.ShowImplicits", action)) {
          settings.SUGGEST_IMPLICIT_HINTS = false
        } else if ((isAction("ExpressionTypeInfo", action) && isInScalaFile(dataContext) && isInvokedByKeyboard(action, event)) ||
          isAction("Scala.ShowImplicitConversions", action) ||
          isAction("Scala.ShowImplicitArguments", action)) {

          suggestImplicitHints()
          settings.SUGGEST_IMPLICIT_HINTS = false
        }
      } else {
        messageBusConnection.disconnect()
      }
    }
  }

  override def componentsInitialized(): Unit = {
    messageBusConnection = ApplicationManager.getApplication.getMessageBus.connect()
    messageBusConnection.subscribe(AnActionListener.TOPIC, actionListener)
  }
}

private object ImplicitHintsAdviser {
  private final val Message =
    """<html>
      | <body>
      | <p><strong>Implicit Conversions</strong> and <strong>Implicit Arguments</strong> are deprecated in favor of <strong>View / Show Implicit Hints</strong>.</p>
      | <p>Shortcut for <strong>Implicit Arguments</strong> now shows <strong>Expression Type</strong> (as for other languages).</p>
      | <p><a href="https://www.youtube.com/watch?v=dRiQIo9moSw">Learn more</a></p>
      | </body>
      |</html>""".stripMargin

  private def suggestImplicitHints(): Unit = {
    NotificationUtil
      .builder(project = null, Message)
      .setNotificationType(NotificationType.INFORMATION)
      .setTitle("Did you know?")
      .show()
  }

  private def isAction(actionId: String, action: AnAction): Boolean =
    action == ActionManager.getInstance.getAction(actionId).ensuring(_ != null, actionId)

  private def isInScalaFile(context: DataContext): Boolean =
    context.getData(CommonDataKeys.PSI_FILE).isInstanceOf[ScalaFile]

  private def isInvokedByKeyboard(action: AnAction, event: AnActionEvent): Boolean = {
    event.getInputEvent match {
      case keyEvent: KeyEvent =>
        action.getShortcutSet.getShortcuts.exists {
          case shortcut: KeyboardShortcut if shortcut.isKeyboard =>
            shortcut.getFirstKeyStroke == KeyStroke.getKeyStroke(keyEvent.getKeyCode, keyEvent.getModifiers)
          case _ => false
        }
      case _ => false
    }
  }
}
