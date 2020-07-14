package org.jetbrains.plugins.scala.editor.documentationProvider.listeners

import com.intellij.codeInsight.documentation.render.DocRenderManager
import com.intellij.ide.{BrowserUtil, IdeBundle}
import com.intellij.notification._
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.fileEditor.{FileEditorManager, FileEditorManagerListener}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.editor.ScalaEditorBundle
import org.jetbrains.plugins.scala.editor.documentationProvider.listeners.ScalaDocRenderInEditorAdvertiser._
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.settings.{ScalaApplicationSettings, ShowSettingsUtilImplExt}

final class ScalaDocRenderInEditorAdvertiser(project: Project) extends FileEditorManagerListener {

  override def fileOpened(source: FileEditorManager, file: VirtualFile): Unit =
    if (isNotificationEnabled) {
      if (editorSettings.isDocCommentRenderingEnabled) {
        disableNotification() // do not suggest if user already knows about the setting
      } else if (isScalaFileWithSomeScalaDoc(file, project)) {
        disableNotification()
        suggestInEditorDocRendering(project)
      }
    }
}

object ScalaDocRenderInEditorAdvertiser {

  private val RenderViewHttpsHelpPage =
    "https://www.jetbrains.com/help/idea/working-with-code-documentation.html#toggle-rendered-view"

  private def isNotificationEnabled: Boolean =
    ScalaApplicationSettings.getInstance.SUGGEST_IN_EDITOR_DOC_RENDERING

  private def disableNotification(): Unit =
    ScalaApplicationSettings.getInstance.SUGGEST_IN_EDITOR_DOC_RENDERING = false

  private def editorSettings = EditorSettingsExternalizable.getInstance

  private def isScalaFileWithSomeScalaDoc(file: VirtualFile, project: Project) =
    PsiManager.getInstance(project).findFile(file) match {
      case scalaFile: ScalaFile => scalaFile.depthFirst(_.isInstanceOf[ScDocComment]).nonEmpty
      case _                    => false
    }

  private def suggestInEditorDocRendering(project: Project): Unit = {
    val notification = {
      val group = new NotificationGroup("Scaladoc rendering advertiser", NotificationDisplayType.STICKY_BALLOON, true)
      group.createNotification(ScalaEditorBundle.message("doc.rendering.advertiser.title"), null, null, NotificationType.INFORMATION)
    }

    notification.setCollapseActionsDirection(Notification.CollapseActionsDirection.KEEP_LEFTMOST)

    notification
      .addAction(new EnableAction)
      .addAction(new OpenSettingsAction())
      .addAction(new MoreInfoAction())

    notification.notify(project)
  }

  private class EnableAction extends NotificationAction(ScalaEditorBundle.message("doc.rendering.advertiser.enable")) {
    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
      notification.hideBalloon()
      editorSettings.setDocCommentRenderingEnabled(true)
      DocRenderManager.resetAllEditorsToDefaultState()
    }
  }

  private class OpenSettingsAction extends NotificationAction(ScalaEditorBundle.message("doc.rendering.advertiser.settings")) {
    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
      notification.hideBalloon()
      ShowSettingsUtilImplExt.showSettingsDialog(
        e.getProject,
        classOf[com.intellij.application.options.editor.EditorAppearanceConfigurable],
        IdeBundle.message("checkbox.show.rendered.doc.comments")
      )
    }
  }

  private class MoreInfoAction extends NotificationAction(ScalaEditorBundle.message("doc.rendering.advertiser.more.info")) {
    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit =
      BrowserUtil.browse(RenderViewHttpsHelpPage)
  }
}
