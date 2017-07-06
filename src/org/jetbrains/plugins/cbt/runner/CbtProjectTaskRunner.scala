package org.jetbrains.plugins.cbt.runner

import java.util

import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.{DefaultJavaProgramRunner, RunManagerImpl, RunnerAndConfigurationSettingsImpl}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.{ExecutionManager, Executor}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.task._
import com.intellij.task.impl.ExecuteRunConfigurationTaskImpl
import org.jetbrains.plugins.cbt._
import org.jetbrains.plugins.cbt.project.settings.CbtProjectSettings

import scala.collection.JavaConverters._

class CbtProjectTaskRunner extends ProjectTaskRunner {

  override def canRun(projectTask: ProjectTask): Boolean = {
    Option(projectTask).flatMap {
      case task: ModuleBuildTask =>
        Some(task.getModule.getProject)
      case task: ExecuteRunConfigurationTaskImpl =>
        val taskSupported = task.getRunProfile match {
          case _: ApplicationConfiguration => true
          case _ => false
        }
        if (taskSupported)
          Some(task.getSettings.getConfiguration.getProject)
        else
          None
      case _ => None
    }.exists { project =>
      val projectSettings = CbtProjectSettings.getInstance(project, project.getBasePath)
      project.isCbtProject &&
        projectSettings.useCbtForInternalTasks
    }
  }

  override def run(project: Project, context: ProjectTaskContext,
                   callback: ProjectTaskNotification,
                   tasks: util.Collection[_ <: ProjectTask]): Unit = {
    FileDocumentManager.getInstance().saveAllDocuments()
    tasks.asScala
      .collect {
        case task: ModuleBuildTask => task
        case task: ExecuteRunConfigurationTask => task
      }
      .headOption
      .foreach(handleTask(project, callback))
  }

  private def handleTask(project: Project, callback: ProjectTaskNotification)(task: ProjectTask) = {
    val taskCallback =
      Option(callback).map(f => () => f.finished(new ProjectTaskResult(false, 0, 0)))
    val environment = createExecutionEnvironment(project, task, taskCallback)
    ExecutionManager.getInstance(project).restartRunProfile(environment)
  }

  private def createExecutionEnvironment(project: Project, projectTask: ProjectTask, callback: Option[() => Unit]) = {
    val configuration = projectTask match {
      case task: ModuleBuildTask =>
        new CbtBuildConfigurationFactory("compile", callback, CbtConfigurationType.getInstance)
          .createTemplateConfiguration(project)
      case task: ExecuteRunConfigurationTask =>
        new CbtBuildConfigurationFactory("run", callback, CbtConfigurationType.getInstance)
          .createTemplateConfiguration(project)
    }
    val runnerSettings = new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), configuration)
    val environment = new ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance, DefaultJavaProgramRunner.getInstance, runnerSettings, project)
    environment
  }

  override def createExecutionEnvironment(project: Project,
                                          task: ExecuteRunConfigurationTask,
                                          executor: Executor): ExecutionEnvironment = {
    createExecutionEnvironment(project, task, None)
  }
}
