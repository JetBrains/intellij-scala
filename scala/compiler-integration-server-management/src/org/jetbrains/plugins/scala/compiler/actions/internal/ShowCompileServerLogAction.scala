package org.jetbrains.plugins.scala.compiler.actions.internal

import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnActionEvent}
import com.intellij.openapi.project.{DumbAwareAction, Project}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, CompilerIntegrationBundle}
import org.jetbrains.plugins.scala.extensions.executeOnPooledThread
import org.jetbrains.plugins.scala.server.CompileServerLog

/**
 * Similar to [[com.intellij.ide.actions.ShowLogAction]].
 */
final class ShowCompileServerLogAction extends DumbAwareAction {

  // Called in the constructor
  getTemplatePresentation.setText(displayName(skipDetection = true))

  override def actionPerformed(e: AnActionEvent): Unit = {
    showLog(e.getProject)
  }

  override def update(e: AnActionEvent): Unit = {
    val presentation = e.getPresentation
    presentation.setVisible(isSupported)
    presentation.setText(displayName(skipDetection = false))
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  private def displayName(skipDetection: Boolean): String =
    CompilerIntegrationBundle.message("show.compile.server.log.action.text", RevealFileAction.getFileManagerName(skipDetection))

  private def isSupported: Boolean =
    RevealFileAction.isDirectoryOpenSupported

  private def showLog(@Nullable project: Project): Unit = executeOnPooledThread {
    val logDir = CompileServerLauncher.logDirectory(project)
    if (RevealFileAction.isSupported) {
      RevealFileAction.openFile(CompileServerLog.logFilePath(logDir))
    } else {
      RevealFileAction.openDirectory(logDir)
    }
  }
}
