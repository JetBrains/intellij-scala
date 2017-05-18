package org.jetbrains.sbt.shell.action

import javax.swing.Icon

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{ActionManager, AnActionEvent, IdeActions, Presentation}
import com.intellij.openapi.project.{DumbAwareAction, Project}
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellCommunication, SbtShellToolWindowFactory}


class RestartAction(project: Project) extends DumbAwareAction {
  copyFrom(ActionManager.getInstance.getAction(IdeActions.ACTION_RERUN))

  val templatePresentation: Presentation = getTemplatePresentation
  templatePresentation.setIcon(AllIcons.Actions.Restart)
  templatePresentation.setText("Restart SBT Shell") // TODO i18n / language-bundle
  templatePresentation.setDescription(null)

  def actionPerformed(e: AnActionEvent): Unit = {
    val twm = ToolWindowManager.getInstance(project)
    val toolWindow = twm.getToolWindow(SbtShellToolWindowFactory.ID)
    toolWindow.getContentManager.removeAllContents(true)

    SbtProcessManager.forProject(e.getProject).restartProcess()
  }
}

class StopAction(project: Project) extends DumbAwareAction {
  copyFrom(ActionManager.getInstance.getAction(IdeActions.ACTION_STOP_PROGRAM))
  val templatePresentation: Presentation = getTemplatePresentation
  templatePresentation.setIcon(AllIcons.Process.Stop)
  templatePresentation.setText("Stop SBT Shell") // TODO i18n / language-bundle
  templatePresentation.setDescription(null)

  override def actionPerformed(e: AnActionEvent): Unit = {
    SbtProcessManager.forProject(e.getProject).destroyProcess()
  }
}

class ExecuteTaskAction(task: String, icon: Option[Icon]) extends DumbAwareAction {

  getTemplatePresentation.setIcon(icon.orNull)
  getTemplatePresentation.setText(s"Execute $task")

  override def actionPerformed(e: AnActionEvent): Unit = {
    // TODO execute with indicator
    SbtShellCommunication.forProject(e.getProject).command(task)
  }
}
