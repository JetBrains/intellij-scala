package org.jetbrains.plugins.scala.compiler

import com.intellij.notification.{Notification, NotificationAction}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

private final class OpenScalaCompileServerSettingsAction(project: Project, filter: String) extends NotificationAction(CompilerIntegrationBundle.message("wrong.jdk.action.open.compile.server.settings")) {
  override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
    notification.expire()
    CompileServerManager.showCompileServerSettingsDialog(project, filter)
  }
}
