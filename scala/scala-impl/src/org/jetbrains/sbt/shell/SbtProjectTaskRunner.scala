package org.jetbrains.sbt.shell

import java.io.File
import java.util

import com.intellij.compiler.impl.CompilerUtil
import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.compiler.ex.CompilerPathsEx
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.{ExternalSystemUtil, ExternalSystemApiUtil => ES}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.{Module, ModuleType}
import com.intellij.openapi.progress.{PerformInBackgroundOption, ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.task._
import org.jetbrains.annotations.Nullable
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.project.SbtProjectSystem
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

    val validTasks = tasks.asScala.collect {
      case task: ModuleBuildTask => task
    }

    // the "build" button in IDEA always runs the build for all individual modules,
    // and may work differently than just calling the products task from the main module in sbt
    val moduleCommands = validTasks.flatMap(buildCommands)
    val modules = validTasks.map(_.getModule)

    // don't run anything if there's no module to run a build for
    // TODO user feedback
    if (moduleCommands.nonEmpty) {

      val command =
        if (moduleCommands.size == 1) moduleCommands.head
        else moduleCommands.mkString("all ", " ", "")

      FileDocumentManager.getInstance().saveAllDocuments()

      // run this as a task (which blocks a thread) because it seems non-trivial to just update indicators asynchronously?
      val task = new CommandTask(project, modules.toArray, command, Option(callback))
      ProgressManager.getInstance().run(task)
    }
  }

  private def buildCommands(task: ModuleBuildTask): Seq[String] = {
    // TODO sensible way to find out what scopes to run it for besides compile and test?
    // TODO make tasks should be user-configurable
    SbtUtil.getSbtModuleData(task.getModule).toSeq.flatMap { sbtModuleData =>
      val scope = SbtUtil.makeSbtProjectId(sbtModuleData)
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

private class CommandTask(project: Project, modules: Array[Module], command: String, callbackOpt: Option[ProjectTaskNotification]) extends
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
          // TODO looks like this isn't called?
          indicator.setIndeterminate(true)
          indicator.setFraction(0.1)
          indicator.setText("building ...")
        case Output(text) =>
          indicator.setText2(text)
        case TaskComplete =>
          indicator.setText("")
      }

      taskResultAggregator(data,event)
    }

    val defaultTaskResult = TaskResultData(aborted = false, 0, 0)
    val failedResult = new ProjectTaskResult(true, 1, 0)

    // TODO consider running module build tasks separately
    // may require collecting results individually and aggregating
    // and shell communication should do proper queueing
    val commandFuture = shell.command(command, defaultTaskResult, resultAggregator, showShell = true)
      .map(data => new ProjectTaskResult(data.aborted, data.errors, data.warnings))
      .recover {
        case _ =>
          // TODO some kind of feedback / rethrow
          failedResult
      }
      .andThen {
        case _ => refreshRoots(modules, indicator)
      }
      .andThen {
        case Success(taskResult) =>
          // TODO progress monitoring
          callbackOpt.foreach(_.finished(taskResult))
          indicator.setFraction(1)
          indicator.setText("sbt build completed")
          indicator.setText2("")
        case Failure(x) =>
          callbackOpt.foreach(_.finished(failedResult))
          indicator.setText("sbt build failed")
          indicator.setText2(x.getMessage)
      }

    // block thread to make indicator available :(
    Await.ready(commandFuture, Duration.Inf)
  }

  // remove this if/when external system handles this refresh on its own
  private def refreshRoots(modules: Array[Module], indicator: ProgressIndicator) = {
    indicator.setText("Synchronizing output directories...")

    // simply refresh all the source roots to catch any generated files -- this MAY have a performance impact
    // in which case it might be necessary to receive the generated sources directly from sbt and refresh them (see BuildManager)
    val info = ProjectDataManager.getInstance().getExternalProjectData(project,SbtProjectSystem.Id, project.getBasePath)
    val allSourceRoots = ES.findAllRecursively(info.getExternalProjectStructure, ProjectKeys.CONTENT_ROOT)
    val generatedSourceRoots = allSourceRoots.asScala.flatMap { node =>
      val data = node.getData
      // sbt-side generated sources are still imported as regular sources
      val generated = data.getPaths(ExternalSystemSourceType.SOURCE_GENERATED).asScala
      val regular = data.getPaths(ExternalSystemSourceType.SOURCE).asScala
      generated ++ regular
    }.map(_.getPath).toSeq.distinct

    val outputRoots = CompilerPathsEx.getOutputPaths(modules)
    val toRefresh = generatedSourceRoots ++ outputRoots

    CompilerUtil.refreshOutputRoots(toRefresh.asJavaCollection)
    val toRefreshFiles = toRefresh.map(new File(_)).asJava
    LocalFileSystem.getInstance().refreshIoFiles(toRefreshFiles, true, true, null)

    indicator.setText("")
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