package org.jetbrains.bsp

import java.io.File
import java.util
import java.util.UUID

import ch.epfl.scala.bsp.endpoints
import ch.epfl.scala.bsp.schema.{BuildTargetIdentifier, CompileParams, CompileReport}
import com.intellij.execution.process.AnsiEscapeDecoder.ColoredTextAcceptor
import com.intellij.execution.process.{AnsiEscapeDecoder, ProcessOutputTypes}
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil => ES}
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.progress.{PerformInBackgroundOption, ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.task._
import monix.eval
import monix.execution.{ExecutionModel, Scheduler}
import org.jetbrains.bsp.BspProjectTaskRunner.BspTask
import org.jetbrains.ide.PooledThreadExecutor
import org.jetbrains.plugins.scala.build.{BuildFailureException, BuildMessages, BuildToolWindowReporter, IndicatorReporter}
import org.langmeta.jsonrpc._
import org.langmeta.lsp._

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class BspProjectTaskRunner extends ProjectTaskRunner {

  override def canRun(projectTask: ProjectTask): Boolean = projectTask match {
    case task: ModuleBuildTask =>
      val module = task.getModule
      val moduleType = ModuleType.get(module)
      moduleType match {
        case _ : BspSyntheticModuleType => false
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

    private var buildMessages: BuildMessages = BuildMessages.empty

    private val taskId: UUID = UUID.randomUUID()
    private val report = new BuildToolWindowReporter(project, taskId, "bsp build")

    private def compileRequest(implicit client: LanguageClient): eval.Task[Either[Response.Error, CompileReport]] =
      endpoints.BuildTarget.compile.request(CompileParams(targets))

    private val services = Services.empty
      .notification(Window.logMessage) { params => report.output(params.message) }
      .notification(Window.showMessage) { params =>
        // TODO handle message type (warning, error etc) in output
        val text = params.message
        report.output(text)

        // TODO build toolwindow log supports ansi colors, but not some other stuff
        val textNoAnsiAcceptor = new TextCollector
        new AnsiEscapeDecoder().escapeText(text, ProcessOutputTypes.STDOUT, textNoAnsiAcceptor)
        val textNoAnsi = textNoAnsiAcceptor.result

        buildMessages = buildMessages.appendMessage(textNoAnsi)
        import org.langmeta.lsp.MessageType._
        buildMessages =
          params.`type` match {
            case Error =>
              report.error(textNoAnsi)
              buildMessages.addError(text)
            case Warning =>
              report.warning(textNoAnsi)
              buildMessages.addWarning(text)
            case Info =>
              buildMessages
            case Log =>
              buildMessages
          }
      }
      .notification(TextDocument.publishDiagnostics) { params =>
        params.diagnostics.foreach { diagnostic =>
          val severity = diagnostic.severity.map(s => s"${s.toString}: ").getOrElse("")
          val range = diagnostic.range.start.line.toString // TODO full position output
        val text = s"$severity($range)${diagnostic.message}"

          report.output(text)
          buildMessages = buildMessages.appendMessage(text)

          import org.langmeta.lsp.DiagnosticSeverity._
          buildMessages =
            diagnostic.severity.map {
              case Error =>
                report.error(text)
                buildMessages.addError(text)
              case Warning =>
                buildMessages.addWarning(text)
              case Information =>
                buildMessages
              case Hint =>
                buildMessages
            }
              .getOrElse(buildMessages)
        }
      }

    override def run(indicator: ProgressIndicator): Unit = {
      val reportIndicator = new IndicatorReporter(indicator)

      val projectRoot = new File(project.getBasePath)

      val buildTask = for {
        session <- BspCommunication.prepareSession(projectRoot)
        compiled <- session.run(services, compileRequest(_))
      } yield {
        compiled
      }

      reportIndicator.start()
      report.start()

      val result = Await.result(buildTask.runAsync, Duration.Inf)

      result match {
        case Left(errorResponse) =>
          val message = errorResponse.error.message
          val failure = BuildFailureException(message)
          report.error(message)
          report.finishWithFailure(failure)
          reportIndicator.finishWithFailure(failure)
        case Right(compileReport) =>
          compileReport.items.foreach { item =>
            val targetStr = item.target.map(t => s"$t : ").getOrElse("")
            report.output(s"${targetStr}completed compile with ${item.warnings} warnings and ${item.errors} errors in ${item.time}ms")
          }
          report.finish(buildMessages)
          reportIndicator.finish(buildMessages)
      }
    }


  }

  private class TextCollector extends ColoredTextAcceptor {
    private val builder = StringBuilder.newBuilder

    override def coloredTextAvailable(text: String, attributes: Key[_]): Unit =
      builder.append(text)

    def result: String = builder.result()
  }


}