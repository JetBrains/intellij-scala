package org.jetbrains.plugins.scala.editor

import com.intellij.ide.BrowserUtil
import com.intellij.notification._
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.settings.{ScalaApplicationSettings, ScalaEditorSmartKeysConfigurable, ShowSettingsUtilImplExt}
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups

object AutoBraceAdvertiser {
  def advertiseAutoBraces(project: Project): Unit = {
    if (shouldAdvertiseAutoBraces) {
      suggestAutoBraces(project)
      disableNotification()
    }
  }

  def disableNotification(): Unit =
    ScalaApplicationSettings.getInstance.SUGGEST_AUTOBRACE_INSERTION = false

  def shouldAdvertiseAutoBraces: Boolean =
    isNotificationEnabled && ScalaApplicationSettings.getInstance.HANDLE_BLOCK_BRACES_INSERTION_AUTOMATICALLY

  private def isNotificationEnabled: Boolean =
    ScalaApplicationSettings.getInstance.SUGGEST_AUTOBRACE_INSERTION

  private val AutoBraceHttpsBlogPage =
    "https://blog.jetbrains.com/scala/2020/07/22/intellij-scala-plugin-2020-2-indentation-based-brace-handling/"

  private def suggestAutoBraces(project: Project): Unit = {
    val notification = {
      val group = ScalaNotificationGroups.stickyBalloonGroup
      group.createNotification(ScalaEditorBundle.message("the.curly.braces.can.be.added.or.removed.automatically"), NotificationType.INFORMATION)
    }

    notification.setCollapseDirection(Notification.CollapseActionsDirection.KEEP_LEFTMOST)

    notification
      .addAction(new OpenSettingsAction())
      .addAction(new MoreInfoAction())

    notification.notify(project)
  }

  private class OpenSettingsAction extends NotificationAction(ScalaEditorBundle.message("doc.rendering.advertiser.settings")) {
    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
      notification.hideBalloon()
      ShowSettingsUtilImplExt.showSettingsDialog(
        e.getProject,
        classOf[ScalaEditorSmartKeysConfigurable],
        ""
      )
    }
  }

  private class MoreInfoAction extends NotificationAction(ScalaEditorBundle.message("doc.rendering.advertiser.more.info")) {
    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit =
      BrowserUtil.browse(AutoBraceHttpsBlogPage)
  }
}
