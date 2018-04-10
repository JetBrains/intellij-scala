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
import org.langmeta.jsonrpc._
import org.langmeta.lsp.{LanguageClient, Window}

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
      val moduleId = ES.getExternalProjectId(task.getModule)
      BuildTargetIdentifier(moduleId)
    }.toSeq

    implicit val scheduler: Scheduler = Scheduler(PooledThreadExecutor.INSTANCE, ExecutionModel.AlwaysAsyncExecution)

    val bspTask = new BspTask(project, targets)
    ProgressManager.getInstance().run(bspTask)
  }
}

object BspProjectTaskRunner {

  class BspTask[T](project: Project, targets: Seq[BuildTargetIdentifier])(implicit scheduler: Scheduler)
    extends Task.Backgroundable(project, "bsp build", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {

    private val taskId: UUID = UUID.randomUUID()
    private val report = new BuildToolWindowReporter(project, taskId, "bsp build")

    private def compileRequest(implicit client: LanguageClient): eval.Task[Either[Response.Error, CompileReport]] =
      endpoints.BuildTarget.compile.request(CompileParams(targets))

    private val services = Services.empty
      .notification(Window.logMessage) { params => report.output(params.message) }

    override def run(indicator: ProgressIndicator): Unit = {
      val reportIndicator = new IndicatorReporter(indicator)
      val messages = BuildMessages.empty

      val projectRoot = new File(project.getBasePath)

      val buildTask = for {
        session <- BspCommunication.prepareSession(projectRoot)
        _ = report.output("session prepared")
        compiled <- session.run(services, compileRequest(_))
      } yield {
        report.output("data received")
        compiled
      }

      reportIndicator.start()
      report.start()

      val result = Await.result(buildTask.runAsync, Duration.Inf)

      result match {
        case Left(errorResponse) =>
          val message = errorResponse.error.message
          report.error(message)
          report.finishWithFailure(BuildFailureException(message))
        case Right(compileReport) =>
          compileReport.items.foreach { item =>
            report.output(s"${item.target}: completed compile with ${item.warnings} warnings and ${item.errors} errors in ${item.time}ms")
          }
          report.finish(messages)
      }
      reportIndicator.finish(messages)
    }


  }
}