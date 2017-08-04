package org.jetbrains.plugins.cbt.runner.action

import com.intellij.execution.ExecutionManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.runner.{CbtProjectTaskRunner, TaskModuleData}

class DebugTaskAction(task: String, taskModuleData: TaskModuleData, project: Project)
  extends AnAction("Debug", "Debug", AllIcons.General.Debug) {
  override def actionPerformed(e: AnActionEvent): Unit = {
    val environment = CbtProjectTaskRunner.createDebugExecutionEnv(task, taskModuleData, project)
    ExecutionManager.getInstance(project).restartRunProfile(environment)
  }
}
