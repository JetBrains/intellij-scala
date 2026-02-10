package org.jetbrains.plugins.scala.compiler.actions.internal

import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.idea.LoggerFactory
import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnActionEvent}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.{FileEditorManager, TextEditor}
import com.intellij.openapi.project.{DumbAwareAction, Project}
import com.intellij.openapi.util.{Condition, NlsSafe}
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtil}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, CompilerIntegrationBundle}
import org.jetbrains.plugins.scala.extensions.executeOnPooledThread
import org.jetbrains.plugins.scala.server.CompileServerLog

import java.io.IOException
import java.nio.charset.StandardCharsets
import scala.annotation.nowarn

/**
 * Similar to [[com.intellij.internal.OpenLogAction]].
 */
final class OpenCompileServerLogAction extends DumbAwareAction {

  // Called in the constructor
  getTemplatePresentation.setText(CompilerIntegrationBundle.message("open.compile.server.log.action.text"))

  override def actionPerformed(e: AnActionEvent): Unit = {
    val project = e.getProject
    if (project == null) return
    openLogInEditor(project)
  }

  override def update(e: AnActionEvent): Unit = {
    e.getPresentation.setEnabled(e.getProject != null)
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  private def openLogInEditor(@NotNull project: Project): Unit = executeOnPooledThread {
    val logDir = CompileServerLauncher.logDirectory(project)
    val logFilePath = CompileServerLog.logFilePath(logDir)
    val file = LocalFileSystem.getInstance.refreshAndFindFileByNioFile(logFilePath)
    if (file != null) {
      VfsUtil.markDirtyAndRefresh(true, false, false, file)
      val editors = FileEditorManager.getInstance(project).openFile(file, true)
      editors match {
        case Array(te: TextEditor, _*) => scrollToLastIDEStart(te)
        case _ => PsiNavigationSupport.getInstance.createNavigatable(project, file, -1).navigate(true)
      }
    } else {
      @NlsSafe val title = "Cannot find '" + LoggerFactory.getLogFilePath + "'"
      @NlsSafe val empty = ""
      Notifications.Bus.notify(
        new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, title, empty, NotificationType.INFORMATION): @nowarn("cat=deprecation"),
        project
      )
    }
  }

  private def scrollToLastIDEStart(editor: TextEditor): Unit = {
    try {
      val bytes = editor.getFile.contentsToByteArray(true)
      val log = new String(bytes, StandardCharsets.UTF_8)
      // Scroll to the bottom of the file.
      val index = log.length - 1
      if (index >= 0) {
        val condition: Condition[?] = _ => editor.getEditor.isDisposed
        ApplicationManager.getApplication.invokeLater(() => {
          editor.getEditor.getCaretModel.moveToOffset(index)
          editor.getEditor.getScrollingModel.scrollToCaret(ScrollType.CENTER_UP)
        }, condition)
      }
    } catch {
      case _: IOException =>
    }
  }
}
