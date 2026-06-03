package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.notification.{Notification, NotificationAction}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls

class OpenFileNotificationActon(project: Project, vFile: VirtualFile, offset: Int, @Nls title: String)
  extends NotificationAction(title) {

  override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
    new OpenFileDescriptor(project, vFile, offset).navigate(true)
    notification.expire()
  }
}