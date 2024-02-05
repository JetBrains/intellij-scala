package org.jetbrains.plugins.scala

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups

import java.awt.datatransfer.StringSelection
import java.awt.{Desktop, Toolkit}
import java.net.URI

object DesktopUtils {

  def browse(project: Project, url: String): Unit = {
    val supported = Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE)

    if (supported)
      Desktop.getDesktop.browse(new URI(url))
    else {
      ScalaNotificationGroups.scalaGeneral
        .createNotification(
          ScalaBundle.message("title.problem.opening.web.page"),
          ScalaBundle.message("html.unable.to.launch.web.browser", url),
          NotificationType.WARNING
        )
        .addAction(new AnAction(ScalaBundle.message("copy.link.to.clipboard")) {
          override def actionPerformed(e: AnActionEvent): Unit = {
            val clipboard = Toolkit.getDefaultToolkit.getSystemClipboard
            clipboard.setContents(new StringSelection(url), null)
          }

          override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT
        })
        .notify(project)
    }
  }
}
