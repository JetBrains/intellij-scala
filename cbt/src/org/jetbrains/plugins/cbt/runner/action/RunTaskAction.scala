package org.jetbrains.plugins.cbt.runner.action

import com.intellij.execution.ExecutionManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import org.jetbrains.plugins.cbt.runner.{CbtProjectTaskRunner, CbtTask}

class RunTaskAction(task: CbtTask)
  extends AnAction(s"Run task '${task.name}'", s"Run task '${task.name}'", AllIcons.General.Run) {
  override def actionPerformed(e: AnActionEvent): Unit = {
    val environment = CbtProjectTaskRunner.createExecutionEnv(task)
    ExecutionManager.getInstance(task.project).restartRunProfile(environment)
  }
}
