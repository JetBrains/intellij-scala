package org.jetbrains.sbt.shell

import java.net.URI
import java.util

import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager
import com.intellij.openapi.externalSystem.util.{ExternalSystemUtil, ExternalSystemApiUtil => ES}
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.progress.{PerformInBackgroundOption, ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.task._
import com.intellij.util.BooleanFunction
import org.jetbrains.annotations.Nullable
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data.SbtModuleData
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.settings.SbtSystemSettings
import org.jetbrains.sbt.shell.SbtShellCommunication._

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

/**
  * Created by jast on 2016-11-25.
  */
class SbtProjectTaskRunner extends ProjectTaskRunner {

  // will override the usual jps build thingies
  override def canRun(projectTask: ProjectTask): Boolean = projectTask match {
    case task: ModuleBuildTask =>
      val module = task.getModule
      ModuleType.get(module) match {
          // TODO Android AARs are currently imported as modules. need a way to filter them away before building
        case _: SbtModuleType =>
          // SbtModuleType actually denotes `-build` modules, which are not part of the regular build
          false
        case _ =>
          val project = task.getModule.getProject
          val projectSettings = SbtSystemSettings.getInstance(project).getLinkedProjectSettings(module)

          projectSettings.exists(_.useSbtShell) &&
          ES.isExternalSystemAwareModule(SbtProjectSystem.Id, module)
      }
    case _: ArtifactBuildTask =>
      // TODO should sbt handle this?
      false
    case _: ExecuteRunConfigurationTask =>
      // TODO this includes tests (and what else?). sbt should handle it and test output should be parsed
      false
    case _ => false
  }

  override def run(project: Project,
                   context: ProjectTaskContext,
                   callback: ProjectTaskNotification,
                   tasks: util.Collection[_ <: ProjectTask]): Unit = {

    val callbackOpt = Option(callback)

    // the "build" button in IDEA always runs the build for all individual modules,
    // and may work differently than just calling the products task from the main module in sbt
    val moduleCommands = tasks.asScala.flatMap {
      case task: ModuleBuildTask => buildCommands(task)
      case _ => Seq.empty[String]
    }

    // don't run anything if there's no module to run a build for
    // TODO user feedback
    if (moduleCommands.nonEmpty) {

      val command =
        if (moduleCommands.size == 1) moduleCommands.head
        else moduleCommands.mkString("; ", "; ", "")

      // run this as a task (which blocks a thread) because it seems non-trivial to just update indicators asynchronously?
      val task = new CommandTask(project, command, callbackOpt)
      ProgressManager.getInstance().run(task)
    }
  }

  private def buildCommands(task: ModuleBuildTask): Seq[String] = {
    val module = task.getModule
    val project = module.getProject
    val moduleId = ES.getExternalProjectId(module) // nullable, but that's okay for use in predicate

    // seems hacky. but it seems there isn't yet any better way to get the data for selected module?
    val predicate = new BooleanFunction[DataNode[ModuleData]] {
      override def fun(s: DataNode[ModuleData]): Boolean = s.getData.getId == moduleId
    }

    val emptyURI = new URI("")
    val dataManager = ProjectDataManager.getInstance()

    // TODO instead of silently not running a task, collect failures, report to user
    val projectScope = for {
      projectInfo <- Option(dataManager.getExternalProjectData(project, SbtProjectSystem.Id, project.getBasePath))
      projectStructure <- Option(projectInfo.getExternalProjectStructure)
      moduleDataNode <- Option(ES.find(projectStructure, ProjectKeys.MODULE, predicate))
      moduleSbtDataNode <- Option(ES.find(moduleDataNode, SbtModuleData.Key))
      data = {
        dataManager.ensureTheDataIsReadyToUse(moduleSbtDataNode)
        moduleSbtDataNode.getData
      }
      // buildURI should never be empty for true sbt projects, but filtering here handles synthetic projects
      // created from AAR files. Should implement a more elegant solution for AARs.
      uri <- Option(data.buildURI) if uri != emptyURI
    } yield {
      val id = data.id
      s"{$uri}$id"
    }

    // TODO sensible way to find out what scopes to run it for besides compile and test?
    projectScope.toSeq.flatMap { scope =>
      // `products` task is a little more general than just `compile`
      Seq(s"$scope/products", s"$scope/test:products")
    }
  }

  @Nullable
  override def createExecutionEnvironment(project: Project,
                                          task: ExecuteRunConfigurationTask,
                                          executor: Executor): ExecutionEnvironment = {

    val taskSettings = new ExternalSystemTaskExecutionSettings
    val executorId = Option(executor).map(_.getId).getOrElse(DefaultRunExecutor.EXECUTOR_ID)

    ExternalSystemUtil.createExecutionEnvironment(
      project,
      SbtProjectSystem.Id,
      taskSettings, executorId
    )
  }

}

private class CommandTask(project: Project, command: String, callbackOpt: Option[ProjectTaskNotification]) extends
  Task.Backgroundable(project, "sbt build", false, PerformInBackgroundOption.ALWAYS_BACKGROUND) {

  import CommandTask._

  override def run(indicator: ProgressIndicator): Unit = {
    indicator.setIndeterminate(true)
    indicator.setFraction(0) // TODO how does the fraction thing work?
    indicator.setText("queued sbt build ...")

    val shell = SbtShellCommunication.forProject(project)

    val resultAggregator: (TaskResultData,ShellEvent) => TaskResultData = { (data,event) =>
      event match {
        case TaskStart =>
          indicator.setIndeterminate(true)
          indicator.setFraction(0.1)
          indicator.setText("building ...")
        case Output(text) =>
          indicator.setText2(text)
        case TaskComplete =>
      }

      taskResultAggregator(data,event)
    }

    val defaultTaskResult = TaskResultData(aborted = false, 0, 0)

    // TODO consider running module build tasks separately
    // may require collecting results individually and aggregating
    // and shell communication should do proper queueing
    val commandFuture = shell.command(command, defaultTaskResult, resultAggregator, showShell = true)
      .map(data => new ProjectTaskResult(data.aborted, data.errors, data.warnings))
      .recover {
        case _ =>
          // TODO some kind of feedback / rethrow
          new ProjectTaskResult(true, 1, 0)
      }
      .andThen {
        case Success(taskResult) =>
          // TODO progress monitoring
          callbackOpt.foreach(_.finished(taskResult))
          indicator.setFraction(1)
          indicator.setText("sbt build completed")
          indicator.setText2("")
        case Failure(x) =>
          indicator.setText("sbt build failed")
          indicator.setText2(x.getMessage)
        // TODO some kind of feedback / rethrow
      }

    // block thread to make indicator available :(
    Await.ready(commandFuture, Duration.Inf)
  }
}

object CommandTask {

  private case class TaskResultData(aborted: Boolean, errors: Int, warnings: Int)

  private val taskResultAggregator: EventAggregator[TaskResultData] = (result, event) =>
    event match {
      case TaskStart => result
      case TaskComplete => result
      case Output(text) =>
        if (text startsWith "[error]")
          result.copy(errors = result.errors+1)
        else if (text startsWith "[warning]")
          result.copy(warnings = result.warnings+1)
        else result
    }
}