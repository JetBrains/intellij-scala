package org.jetbrains.sbt.shell.action

import javax.swing.Icon

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.{ExecutionManager, Executor}
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{ActionManager, AnActionEvent, IdeActions, Presentation}
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellCommunication, SbtShellRunner}

class AutoCompleteAction extends DumbAwareAction {
  override def actionPerformed(e: AnActionEvent): Unit = {
    // TODO call code completion (ctrl+space by default)
  }
}

class RestartAction(runner: SbtShellRunner, executor: Executor, contentDescriptor: RunContentDescriptor) extends DumbAwareAction {
  copyFrom(ActionManager.getInstance.getAction(IdeActions.ACTION_RERUN))

  val templatePresentation: Presentation = getTemplatePresentation
  templatePresentation.setIcon(AllIcons.Actions.Restart)
  templatePresentation.setText("Restart SBT Shell") // TODO i18n / language-bundle
  templatePresentation.setDescription(null)

  def actionPerformed(e: AnActionEvent): Unit = {
    val removed = ExecutionManager.getInstance(runner.getProject)
      .getContentManager
      .removeRunContent(executor, contentDescriptor)

    if (removed) SbtProcessManager.forProject(e.getProject).restartProcess()
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
