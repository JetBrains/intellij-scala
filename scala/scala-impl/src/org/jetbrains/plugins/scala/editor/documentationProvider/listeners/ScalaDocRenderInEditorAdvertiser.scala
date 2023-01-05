package org.jetbrains.plugins.scala.editor.documentationProvider.listeners

import com.intellij.codeInsight.documentation.render.DocRenderManager
import com.intellij.ide.{BrowserUtil, IdeBundle}
import com.intellij.notification._
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.fileEditor.{FileEditorManager, FileEditorManagerListener}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiElement, PsiManager}
import org.jetbrains.plugins.scala.editor.ScalaEditorBundle
import org.jetbrains.plugins.scala.editor.documentationProvider.listeners.ScalaDocRenderInEditorAdvertiser._
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScTypeDefinition}
import org.jetbrains.plugins.scala.settings.{ScalaApplicationSettings, ShowSettingsUtilImplExt}
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups

final class ScalaDocRenderInEditorAdvertiser(project: Project) extends FileEditorManagerListener {

  override def fileOpened(source: FileEditorManager, file: VirtualFile): Unit =
    if (isNotificationEnabled) {
      if (editorSettings.isDocCommentRenderingEnabled) {
        disableNotification() // do not suggest if user already knows about the setting
      }
      else if (isScalaFileWithSomeScalaDocComment(file, project)) {
        suggestInEditorDocRendering(project)
        disableNotification()
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

  private def isScalaFileWithSomeScalaDocComment(file: VirtualFile, project: Project) =
    PsiManager.getInstance(project).findFile(file) match {
      case scalaFile: ScalaFile => scalaFile.children.exists(hasScalaDocComment)
      case _                    => false
    }

  private def hasScalaDocComment(element: PsiElement): Boolean = {
    val hasDoc = element match {
      case owner: ScDocCommentOwner => owner.getDocComment != null
      case _                        => false
    }
    hasDoc || (element match {
      case typeDef: ScTypeDefinition =>
        typeDef.members.exists(hasScalaDocComment)
      case _ => false
    })
  }

  private def suggestInEditorDocRendering(project: Project): Unit = {
    val notification = {
      ScalaNotificationGroups
        .scalaFeaturesAdvertiser
        .createNotification(ScalaEditorBundle.message("doc.rendering.advertiser.title"), NotificationType.INFORMATION)
    }

    notification.setCollapseDirection(Notification.CollapseActionsDirection.KEEP_LEFTMOST)

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
