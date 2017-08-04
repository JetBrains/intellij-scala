package org.jetbrains.plugins.cbt.runner

import java.util

import com.intellij.debugger.impl.{GenericDebuggerRunner, GenericDebuggerRunnerSettings}
import com.intellij.execution._
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.{DefaultJavaProgramRunner, RunManagerImpl, RunnerAndConfigurationSettingsImpl}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.task._
import com.intellij.task.impl.ExecuteRunConfigurationTaskImpl
import com.intellij.util.Alarm
import org.jetbrains.plugins.cbt._
import org.jetbrains.plugins.cbt.project.settings.CbtProjectSettings
import org.jetbrains.plugins.cbt.runner.internal.{CbtBuildConfigurationFactory, CbtBuildConfigurationType, CbtDebugConfigurationFactory, CbtDebugConfigurationType}

import scala.collection.JavaConverters._

class CbtProjectTaskRunner extends ProjectTaskRunner {
  val alarm = new Alarm

  override def canRun(projectTask: ProjectTask): Boolean = {
    Option(projectTask).flatMap {
      case task: ModuleBuildTask =>
        Some(task.getModule.getProject)
      case task: ExecuteRunConfigurationTaskImpl =>
        val taskSupported = task.getRunProfile match {
          case _: ApplicationConfiguration => true
          case _: CbtRunConfiguration
            if task.getRunnerSettings.isInstanceOf[GenericDebuggerRunnerSettings] =>
            true
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

  override def run(project: Project,
                   context: ProjectTaskContext,
                   callback: ProjectTaskNotification,
                   tasks: util.Collection[_ <: ProjectTask]): Unit = {
    FileDocumentManager.getInstance().saveAllDocuments()
    val moduleBuildTasks = tasks.asScala
      .collect {
        case task: ModuleBuildTask => task
      }
      .toList
    val taskOpt = if (moduleBuildTasks.length == 1)  // If build single module task selected
      moduleBuildTasks.headOption
    else if (moduleBuildTasks.length > 1) // build the whole project selected
      moduleBuildTasks
        .find(_.getModule.getModuleFile.getParent.getCanonicalPath == project.getBaseDir.getCanonicalPath)
    else None
    taskOpt.foreach { task =>
      val workingDir = task.getModule.getModuleFile.getParent.getPath
      val taskModuleData = TaskModuleData(workingDir, task.getModule.getName)
      buildTask(project, taskModuleData, callback)
    }
  }

  private def buildTask(project: Project,
                        taskModuleData: TaskModuleData,
                        callback: ProjectTaskNotification) = {
    val listener = new CbtProcessListener {
      override def onComplete(): Unit =
        Option(callback)
          .foreach { f =>
            val request = new Runnable {
              def run(): Unit = {
                f.finished(new ProjectTaskResult(false, 0, 0))
              }
            }
            alarm.addRequest(request, 500)
          }

      override def onTextAvailable(text: String, stderr: Boolean): Unit = ()
    }
    val environment = CbtProjectTaskRunner.createExecutionEnv("compile", taskModuleData, project, listener)
    ExecutionManager.getInstance(project).restartRunProfile(environment)
  }

  override def createExecutionEnvironment(project: Project,
                                          task: ExecuteRunConfigurationTask,
                                          executor: Executor): ExecutionEnvironment = {
    val debug = task.getRunnerSettings != null
    val (taskName, taskModuleData) = task.getRunProfile match {
      case conf: CbtRunConfiguration =>
        (conf.getTask, TaskModuleData(conf.getWorkingDir, project))
      case conf: ApplicationConfiguration =>
        val mainClass = conf.getMainClass.getQualifiedName
        (s"runMain $mainClass", TaskModuleData(conf.getWorkingDirectory, project))
    }
    if (debug)
      CbtProjectTaskRunner.createDebugExecutionEnv(taskName, taskModuleData,  project)
     else
      CbtProjectTaskRunner.createExecutionEnv(taskName, taskModuleData, project, CbtProcessListener.Dummy)
  }
}



object CbtProjectTaskRunner {
  def createExecutionEnv(task: String, taskModuleData: TaskModuleData, project: Project, listener: CbtProcessListener): ExecutionEnvironment = {
    val projectSettings = CbtProjectSettings.getInstance(project, project.getBasePath)
    val configuration =
        new CbtBuildConfigurationFactory(task, projectSettings.useDirect,
          taskModuleData, Seq.empty, CbtBuildConfigurationType.getInstance, listener)
          .createTemplateConfiguration(project)
    val runnerSettings = new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), configuration)
    val environment = new ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance,
      DefaultJavaProgramRunner.getInstance, runnerSettings, project)
    environment
  }

  def createDebugExecutionEnv(task: String, taskModuleData: TaskModuleData, project: Project): ExecutionEnvironment = {
    val configFactory = new CbtDebugConfigurationFactory(task, taskModuleData, CbtDebugConfigurationType.getInstance)
    val configuration = configFactory.createTemplateConfiguration(project)
    val runnerSettings = new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), configuration)

    val environment = new ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance,
      new GenericDebuggerRunner, runnerSettings, project)
    environment
  }
}















