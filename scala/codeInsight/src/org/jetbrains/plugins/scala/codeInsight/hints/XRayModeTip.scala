package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.notification.{NotificationType, Notifications}
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.settings.{ScalaApplicationSettings, ScalaProjectSettingsConfigurable}
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups

import java.util.function.Consumer

class XRayModeTip extends StartupActivity.DumbAware {
  private var messageBusConnection: Option[MessageBusConnection] = None

  override def runActivity(project: Project): Unit =  {
    if (isEnabled && messageBusConnection.isEmpty) {
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

      override def afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult): Unit = {
        if (isEnabled) {
          if (
            isAction("Scala.TypeInfo", action) ||
            isAction("Scala.ShowImplicitConversions", action) || isAction("Scala.ShowImplicitArguments", action) || isAction("Scala.ShowImplicits", action)) {

            suggestXRayMode(isAction("Scala.TypeInfo", action))
            disable()
          }
        }
      }
    }
  }

  private def isEnabled: Boolean =
    ScalaApplicationSettings.getInstance.SUGGEST_XRAY_MODE

  private def disable(): Unit = {
    ScalaApplicationSettings.getInstance.SUGGEST_XRAY_MODE = false
    messageBusConnection.foreach(_.disconnect())
    messageBusConnection = None
  }

  private def suggestXRayMode(typeInfo: Boolean): Unit = {
    val notification = ScalaNotificationGroups.scalaFeaturesAdvertiser.createNotification(
      if (typeInfo) ScalaCodeInsightBundle.message("xray.mode.tip.title.type.hints")
      else ScalaCodeInsightBundle.message("xray.mode.tip.title.implicit.hints"),
      ScalaHintsSettings.xRayModeShortcut.capitalize,
      NotificationType.INFORMATION)

    notification.addAction(new AnAction(ScalaCodeInsightBundle.message("xray.mode.tip.action.got.it")) {
      override def actionPerformed(e: AnActionEvent): Unit = {
        disable()
        notification.hideBalloon()
      }
    })

    notification.addAction(new AnAction(ScalaCodeInsightBundle.message("xray.mode.tip.action.configure")) {
      override def actionPerformed(e: AnActionEvent): Unit = {
        disable()
        notification.hideBalloon()

        ShowSettingsUtil.getInstance.showSettingsDialog(
          e.getProject,
          classOf[ScalaProjectSettingsConfigurable],
          (_.selectXRayModeTab()): Consumer[ScalaProjectSettingsConfigurable])
      }
    })

    Notifications.Bus.notify(notification)
  }
}