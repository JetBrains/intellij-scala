package org.jetbrains.plugins.cbt.runner

import java.util

import com.intellij.debugger.impl.{GenericDebuggerRunner, GenericDebuggerRunnerSettings}
import com.intellij.execution._
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.{DefaultJavaProgramRunner, RunManagerImpl, RunnerAndConfigurationSettingsImpl}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
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
        task.getRunProfile match {
          case app: ApplicationConfiguration =>
            Some(app.getProject)
          case cbtConf: CbtRunConfiguration if task.getRunnerSettings.isInstanceOf[GenericDebuggerRunnerSettings] =>
            Some(cbtConf.project)
          case _ => None
        }
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
      val taskModuleData =  task.getModule
      buildTask(project, taskModuleData, callback)
    }
  }

  private def buildTask(project: Project,
                        module: Module,
                        callback: ProjectTaskNotification): Unit = {
    val listener = new CbtProcessListener {
      override def onComplete(): Unit =
        Option(callback)
          .foreach { f =>
            val request = runnable {
              f.finished(new ProjectTaskResult(false, 0, 0))
            }

            alarm.addRequest(request, 500)
          }

      override def onTextAvailable(text: String, stderr: Boolean): Unit = ()
    }
    val environment = CbtProjectTaskRunner.createExecutionEnv("compile", module, project, listener)
    ExecutionManager.getInstance(project).restartRunProfile(environment)
  }

  override def createExecutionEnvironment(project: Project,
                                          task: ExecuteRunConfigurationTask,
                                          executor: Executor): ExecutionEnvironment = {
    val debug = task.getRunnerSettings != null
    val (taskName, module) = task.getRunProfile match {
      case conf: CbtRunConfiguration =>
        val module = conf.getModules.head
        (conf.getTask, module)
      case conf: ApplicationConfiguration =>
        val mainClass = conf.getMainClass.getQualifiedName
        val module = conf.getModules.head
        (s"runMain $mainClass", module)
    }
    if (debug)
      CbtProjectTaskRunner.createDebugExecutionEnv(taskName, module,  project)
    else
      CbtProjectTaskRunner.createExecutionEnv(taskName, module, project, CbtProcessListener.Dummy)
  }
}



object CbtProjectTaskRunner {
  def createExecutionEnv(task: String,
                         module: Module,
                         project: Project,
                         listener: CbtProcessListener,
                         options: Seq[String] = Seq.empty): ExecutionEnvironment = {
    val projectSettings = CbtProjectSettings.getInstance(project, project.getBasePath)
    val configuration =
        new CbtBuildConfigurationFactory(task, projectSettings.useDirect,
          module, options, CbtBuildConfigurationType.getInstance, listener)
          .createTemplateConfiguration(project)
    val runnerSettings =
      new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), configuration)
    runnerSettings.setSingleton(true)
    val environment = new ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance,
      DefaultJavaProgramRunner.getInstance, runnerSettings, project)
    environment
  }

  def createDebugExecutionEnv(task: String,
                              module: Module,
                              project: Project,
                              listener: CbtProcessListener = CbtProcessListener.Dummy): ExecutionEnvironment = {
    val configFactory = new CbtDebugConfigurationFactory(task, module, CbtDebugConfigurationType.getInstance)
    val configuration = configFactory.createTemplateConfiguration(project)
    val runnerSettings =
      new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), configuration)
    runnerSettings.setSingleton(true)
    val environment = new ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance,
      new GenericDebuggerRunner, runnerSettings, project)
    environment
  }
}















