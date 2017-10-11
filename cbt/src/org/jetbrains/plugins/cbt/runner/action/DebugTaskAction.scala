package org.jetbrains.plugins.cbt.runner.action

import com.intellij.execution.ExecutionManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.runner.CbtProjectTaskRunner

class DebugTaskAction(task: String, module: Module, project: Project)
  extends AnAction(s"Debug task '$task'", s"Debug task '$task'", AllIcons.General.Debug) {
  override def actionPerformed(e: AnActionEvent): Unit = {
    val environment = CbtProjectTaskRunner.createDebugExecutionEnv(task, module, project)
    ExecutionManager.getInstance(project).restartRunProfile(environment)
  }
}
