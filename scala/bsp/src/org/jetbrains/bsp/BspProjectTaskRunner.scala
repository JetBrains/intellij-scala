package org.jetbrains.bsp

import java.io.File
import java.util
import java.util.UUID

import ch.epfl.scala.bsp.endpoints
import ch.epfl.scala.bsp.schema.{BuildTargetIdentifier, CompileParams, CompileReport}
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil => ES}
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.progress.{PerformInBackgroundOption, ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.task._
import monix.eval
import monix.execution.{ExecutionModel, Scheduler}
import org.jetbrains.bsp.BspProjectTaskRunner.BspTask
import org.jetbrains.ide.PooledThreadExecutor
import org.jetbrains.plugins.scala.build.{BuildFailureException, BuildMessages, BuildToolWindowReporter, IndicatorReporter}
import org.langmeta.jsonrpc.{ErrorCode, ErrorObject, RequestId, Response}
import org.langmeta.lsp.LanguageClient

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class BspProjectTaskRunner extends ProjectTaskRunner {

  override def canRun(projectTask: ProjectTask): Boolean = projectTask match {
    case task: ModuleBuildTask =>
      val module = task.getModule
      ModuleType.get(module) match {
        case _ => ES.isExternalSystemAwareModule(bsp.ProjectSystemId, module)
      }
    case _: ArtifactBuildTask => false
    case _: ExecuteRunConfigurationTask => false
    case _ => false
  }

  override def run(project: Project,
                   projectTaskContext: ProjectTaskContext,
                   projectTaskNotification: ProjectTaskNotification,
                   tasks: util.Collection[_ <: ProjectTask]): Unit = {

    val validTasks = tasks.asScala.collect {
      case task: ModuleBuildTask => task
    }

    val targets = validTasks.map { task =>
      val id = task.getModule.getName
      BuildTargetIdentifier(id)
    }.toSeq

    implicit val scheduler: Scheduler = Scheduler(PooledThreadExecutor.INSTANCE, ExecutionModel.AlwaysAsyncExecution)

    val base = new File(project.getBasePath)
    val init: eval.Task[BspSession] = BspCommunication.initialize(base)

    def compile(implicit client: LanguageClient): eval.Task[Either[Response.Error, CompileReport]] =
      endpoints.BuildTarget.compile.request(CompileParams(targets))

    val compileReport = for {
      session <- init
      compiled <- compile(session.client)
    } yield {
//      Left(Response.Error(ErrorObject(ErrorCode.InternalError, "dummy error", None), RequestId(-1)))
      compiled
    }

    val bspTask = new BspTask(project, compileReport)
    ProgressManager.getInstance().run(bspTask)
  }
}

object BspProjectTaskRunner {

  class BspTask(project: Project, task: monix.eval.Task[scala.Either[Response.Error, CompileReport]])(implicit scheduler: Scheduler)
    extends Task.Backgroundable(project, "bsp build", false, PerformInBackgroundOption.ALWAYS_BACKGROUND) {

    private val taskId: UUID = UUID.randomUUID()

    override def run(indicator: ProgressIndicator): Unit = {
      val reportIndicator = new IndicatorReporter(indicator)
      val report = new BuildToolWindowReporter(project, taskId, "bsp build")
      val messages = BuildMessages.empty

      reportIndicator.start()
      report.start()
      val result = Await.result(task.runAsync, Duration.Inf)
      result match {
        case Left(error) =>
          val message = error.error.message
          report.error(message)
          report.finishWithFailure(BuildFailureException(message))
        case Right(compileReport) =>
          compileReport.items.foreach { item =>
            report.output(s"completed build with ${item.warnings} warnings and ${item.errors} errors in ${item.time}ms")
          }
          report.finish(messages)
      }
      reportIndicator.finish(messages)
    }
  }
}