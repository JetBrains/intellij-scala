package org.jetbrains.plugins.cbt.runner.action

import com.intellij.execution.ExecutionManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.runner.{CbtProcessListener, CbtProjectTaskRunner, TaskModuleData}

class RunTaskAction(task: String, taskModuleData: TaskModuleData, project: Project)
  extends AnAction("Run", "Run", AllIcons.General.Run){
  override def actionPerformed(e: AnActionEvent): Unit = {
    val environment = CbtProjectTaskRunner.createExecutionEnv(task, taskModuleData, project, CbtProcessListener.Dummy)
    ExecutionManager.getInstance(project).restartRunProfile(environment)
  }
}
