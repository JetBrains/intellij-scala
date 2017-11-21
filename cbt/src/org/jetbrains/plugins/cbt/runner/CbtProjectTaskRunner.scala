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
import org.jetbrains.plugins.cbt.runner.internal.{CbtDebugConfigurationFactory, CbtDebugConfigurationType, CbtModuleTaskConfigurationFactory, CbtModuleTaskConfigurationType}

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
      project.isCbtProject
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
    val taskOpt = if (moduleBuildTasks.length == 1) // If build single module task selected
      moduleBuildTasks.headOption
    else if (moduleBuildTasks.length > 1) // build the whole project selected
      moduleBuildTasks
        .find(_.getModule.getModuleFile.getParent.getCanonicalPath == project.getBaseDir.getCanonicalPath)
    else None
    taskOpt.foreach { taskModuleBuildTask =>
      val module = taskModuleBuildTask.getModule
      val projectSettings = CbtProjectSettings.getInstance(project, project.getBasePath)
      val task =
        CbtTask("compile",
          projectSettings.useDirect,
          project,
          moduleOpt = Some(module),
          listenerOpt = Some(projectTaskNotificationToCbtOutputListener(callback))
        )
      val environment = CbtProjectTaskRunner.createExecutionEnv(task)
      ExecutionManager.getInstance(project).restartRunProfile(environment)
    }
  }

  private def projectTaskNotificationToCbtOutputListener(callback: ProjectTaskNotification) =
    new CbtProcessListener {
      override def onComplete(exitCode: Int): Unit =
        Option(callback)
          .foreach { f =>
            val request = runnable {
              f.finished(new ProjectTaskResult(false, 0, 0))
            }

            alarm.addRequest(request, 500)
          }

      override def onTextAvailable(text: String, stderr: Boolean): Unit = ()
    }

  override def createExecutionEnvironment(project: Project,
                                          runConfigurationTask: ExecuteRunConfigurationTask,
                                          executor: Executor): ExecutionEnvironment = {
    val debug = runConfigurationTask.getRunnerSettings != null

    val task: CbtTask = runConfigurationTask.getRunProfile match {
      case conf: CbtRunConfiguration =>
        conf.toCbtTask
      case conf: ApplicationConfiguration =>
        val mainClass = conf.getMainClass.getQualifiedName
        val module = conf.getModules.head
        val projectSettings = CbtProjectSettings.getInstance(project, project.getBasePath)
        CbtTask("runMain",
          projectSettings.useDirect,
          project,
          taskArguments = Seq(mainClass),
          moduleOpt = Some(module))
    }
    if (debug)
      CbtProjectTaskRunner.createDebugExecutionEnv(task)
    else
      CbtProjectTaskRunner.createExecutionEnv(task)
  }
}


object CbtProjectTaskRunner {
  def createExecutionEnv(task: CbtTask): ExecutionEnvironment = {
    val project = task.project
    val configuration =
      new CbtModuleTaskConfigurationFactory(task, CbtModuleTaskConfigurationType.getInstance)
        .createTemplateConfiguration(project)
    val runnerSettings =
      new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), configuration)
    runnerSettings.setSingleton(true)
    val environment = new ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance,
      DefaultJavaProgramRunner.getInstance, runnerSettings, project)
    environment
  }

  def createDebugExecutionEnv(task: CbtTask): ExecutionEnvironment = {
    val project = task.project
    val configFactory =
      new CbtDebugConfigurationFactory(task.copy(useDirect = true, cbtOptions = Seq("-debug")),
        CbtDebugConfigurationType.getInstance)
    val configuration = configFactory.createTemplateConfiguration(project)
    val runnerSettings =
      new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), configuration)
    runnerSettings.setSingleton(true)
    val environment = new ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance,
      new GenericDebuggerRunner, runnerSettings, project)
    environment
  }
}















