package org.jetbrains.plugins.cbt.runner.action

import com.intellij.execution.ExecutionManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.runner.{CbtProcessListener, CbtProjectTaskRunner}

class DebugTaskAction(task: String, workingDir: String, project: Project)
  extends AnAction("Debug", "Debug", AllIcons.General.Debug){
  override def actionPerformed(e: AnActionEvent): Unit = {
    val environment = CbtProjectTaskRunner.createDebugExecutionEnv(task, workingDir, project)
    ExecutionManager.getInstance(project).restartRunProfile(environment)
  }
}
