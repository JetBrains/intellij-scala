package org.jetbrains.plugins.cbt.runner.action

import com.intellij.execution.ExecutionManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import org.jetbrains.plugins.cbt.runner.{CbtProjectTaskRunner, CbtTask}

class DebugTaskAction(task: CbtTask)
  extends AnAction(s"Debug task '${task.name}'", s"Debug task '${task.name}'", AllIcons.General.Debug) {
  override def actionPerformed(e: AnActionEvent): Unit = {
    val environment = CbtProjectTaskRunner.createDebugExecutionEnv(task)
    ExecutionManager.getInstance(task.project).restartRunProfile(environment)
  }
}
