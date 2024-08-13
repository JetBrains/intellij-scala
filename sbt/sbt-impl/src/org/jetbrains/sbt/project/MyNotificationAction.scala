package org.jetbrains.sbt.project

import com.intellij.notification.{Notification, NotificationAction}
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.{ActionManager, AnActionEvent}

class MyNotificationAction(k: Boolean) extends NotificationAction ("dsdsds"){

  override def actionPerformed(anActionEvent: AnActionEvent, notification: Notification): Unit = {
    val action = ActionManager.getInstance.getAction(SbtMigrateConfigurationsAction.ID)
    action.actionPerformed(anActionEvent)
  }
}
