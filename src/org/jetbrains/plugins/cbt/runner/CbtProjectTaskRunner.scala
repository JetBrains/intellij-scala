package org.jetbrains.plugins.cbt.runner

import java.util

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.{RunManagerImpl, RunnerAndConfigurationSettingsImpl}
import com.intellij.execution.runners.{ExecutionEnvironment, ExecutionEnvironmentBuilder}
import com.intellij.execution.{ExecutionManager, Executor}
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemUtil}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.task._
import org.jetbrains.plugins.cbt.project.CbtProjectSystem


class CbtProjectTaskRunner extends ProjectTaskRunner {
  override def canRun(projectTask: ProjectTask): Boolean = projectTask match {
    case task: ModuleBuildTask =>
      val module = task.getModule
      ExternalSystemApiUtil.isExternalSystemAwareModule(CbtProjectSystem.Id, module)
    case _ => false
  }

  override def run(project: Project, context: ProjectTaskContext,
                   callback: ProjectTaskNotification,
                   tasks: util.Collection[_ <: ProjectTask]): Unit = {
    FileDocumentManager.getInstance().saveAllDocuments()

    val configuration = new CbtBuildConfigurationFactory(CbtConfigurationType.getInstance)
      .createTemplateConfiguration(project)

    val runnerSettings = new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), configuration)

    val environment = ExecutionEnvironmentBuilder
      .createOrNull(DefaultRunExecutor.getRunExecutorInstance, runnerSettings)
      .build()

    ExecutionManager.getInstance(project).restartRunProfile(environment)
  }

  override def createExecutionEnvironment(project: Project,
                                          task: ExecuteRunConfigurationTask,
                                          executor: Executor): ExecutionEnvironment = {

    val taskSettings = new ExternalSystemTaskExecutionSettings
    val executorId = Option(executor).map(_.getId).getOrElse(DefaultRunExecutor.EXECUTOR_ID)

    ExternalSystemUtil.createExecutionEnvironment(
      project,
      CbtProjectSystem.Id,
      taskSettings, executorId
    )
  }
}
