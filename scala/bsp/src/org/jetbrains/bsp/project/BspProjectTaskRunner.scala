package org.jetbrains.bsp.project

import java.io.File
import java.util
import java.util.UUID

import cats.data.EitherT
import ch.epfl.scala.bsp._
import com.intellij.build.FilePosition
import com.intellij.execution.process.AnsiEscapeDecoder.ColoredTextAcceptor
import com.intellij.execution.process.{AnsiEscapeDecoder, ProcessOutputTypes}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil => ES}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.progress.{PerformInBackgroundOption, ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.task._
import org.jetbrains.bsp.magic.monixToCats.monixToCatsMonad
import monix.eval
import monix.execution.{ExecutionModel, Scheduler}
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.bsp
import org.jetbrains.bsp.data.BspMetadata
import org.jetbrains.bsp.project.BspProjectTaskRunner.BspTask
import org.jetbrains.bsp.protocol.BspCommunication
import org.jetbrains.ide.PooledThreadExecutor
import org.jetbrains.plugins.scala.build.{BuildFailureException, BuildMessages, BuildToolWindowReporter, IndicatorReporter}

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.meta.jsonrpc.{LanguageClient, Response, Services}
import scala.util.control.NonFatal


class BspProjectTaskRunner extends ProjectTaskRunner {

  override def canRun(projectTask: ProjectTask): Boolean = projectTask match {
    case task: ModuleBuildTask =>
      val module = task.getModule
      val moduleType = ModuleType.get(module)
      moduleType match {
        case _ : BspSyntheticModuleType => false
        case _ => ES.isExternalSystemAwareModule(bsp.ProjectSystemId, module)
      }
    case _: ExecuteRunConfigurationTask => false // TODO support bsp run configs
    case _ => false
  }

  override def run(project: Project,
                   projectTaskContext: ProjectTaskContext,
                   projectTaskNotification: ProjectTaskNotification,
                   tasks: util.Collection[_ <: ProjectTask]): Unit = {

    val validTasks = tasks.asScala.collect {
      case task: ModuleBuildTask => task
    }

    val dataManager = ProjectDataManager.getInstance()

    val targets = validTasks.flatMap { task =>
      val moduleId = ES.getExternalProjectId(task.getModule)

      def predicate(node: DataNode[ModuleData]) = node.getData.getId == moduleId
      // TODO all these options fail silently. collect errors and report something
      val targetIds = for {
        projectInfo <- Option(dataManager.getExternalProjectData(project, bsp.ProjectSystemId, project.getBasePath))
        projectStructure <- Option(projectInfo.getExternalProjectStructure)
        moduleDataNode <- Option(ES.find(projectStructure, ProjectKeys.MODULE, predicate))
        metadata <- Option(ES.find(moduleDataNode, BspMetadata.Key))
      } yield {
        metadata.getData.targetIds
      }

      targetIds.getOrElse(Seq.empty)
    }.toList

    implicit val scheduler: Scheduler = Scheduler(PooledThreadExecutor.INSTANCE, ExecutionModel.AlwaysAsyncExecution)

    // TODO save only documents in affected targets?
    FileDocumentManager.getInstance().saveAllDocuments()
    val bspTask = new BspTask(project, targets, Option(projectTaskNotification))
    ProgressManager.getInstance().run(bspTask)
  }
}

object BspProjectTaskRunner {

  class BspTask[T](project: Project, targets: List[BuildTargetIdentifier], callbackOpt: Option[ProjectTaskNotification])
                  (implicit scheduler: Scheduler)
    extends Task.Backgroundable(project, "bsp build", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {

    private var buildMessages: BuildMessages = BuildMessages.empty

    private val taskId: UUID = UUID.randomUUID()
    private val report = new BuildToolWindowReporter(project, taskId, "bsp build")

    private def compileRequest(implicit client: LanguageClient): eval.Task[Either[Response.Error, CompileResult]] =
      endpoints.BuildTarget.compile.request(CompileParams(targets, None, List.empty)) // TODO support requestId


    private val logger = Logger.getInstance(classOf[BspTask[_]])

    private val services = Services.empty(logger.toScribeLogger)
      .notification(endpoints.Build.logMessage) { params => report.log(params.message) }
      .notification(endpoints.Build.showMessage)(reportShowMessage)
      .notification(endpoints.Build.publishDiagnostics)(reportDiagnostics)
      .notification(endpoints.BuildTarget.compileReport)(reportCompile)

    override def run(indicator: ProgressIndicator): Unit = {
      val reportIndicator = new IndicatorReporter(indicator)

      val projectRoot = new File(project.getBasePath)

      val buildTask = for {
        session <- EitherT(BspCommunication.prepareSession(projectRoot))
        compiled <- EitherT(session.run(services, compileRequest(_))).leftMap(_.toBspError)
      } yield {
        compiled
      }

      reportIndicator.start()
      report.start()

      val projectTaskResult = try {
        val result = Await.result(buildTask.value.runAsync, Duration.Inf)
        result match {
          case Left(error) =>
            val message = error.getMessage
            val failure = BuildFailureException(message)
            report.error(message, None)
            report.finishWithFailure(failure)
            reportIndicator.finishWithFailure(failure)
            new ProjectTaskResult(true, buildMessages.errors.size, buildMessages.warnings.size)
          case Right(compileResult) =>
            // TODO report language specific compile result if available
            report.finish(buildMessages)
            reportIndicator.finish(buildMessages)
            new ProjectTaskResult(buildMessages.aborted, buildMessages.errors.size, buildMessages.warnings.size)
        }

      } catch {
        case NonFatal(err) =>
          report.error(err.getMessage, None)
          report.finishWithFailure(err)
          reportIndicator.finishWithFailure(err)
          new ProjectTaskResult(true, 1, 0)
      }

      callbackOpt.foreach(_.finished(projectTaskResult))
    }

    private def reportShowMessage(params: ShowMessageParams): Unit = {
      // TODO handle message type (warning, error etc) in output
      // TODO use params.requestId to show tree structure
      val text = params.message
      report.log(text)

      // TODO build toolwindow log supports ansi colors, but not some other stuff
      val textNoAnsiAcceptor = new TextCollector
      new AnsiEscapeDecoder().escapeText(text, ProcessOutputTypes.STDOUT, textNoAnsiAcceptor)
      val textNoAnsi = textNoAnsiAcceptor.result

      buildMessages = buildMessages.appendMessage(textNoAnsi)

      import ch.epfl.scala.bsp.MessageType._
      buildMessages =
        params.`type` match {
          case Error =>
            report.error(textNoAnsi, None)
            buildMessages.addError(textNoAnsi)
          case Warning =>
            report.warning(textNoAnsi, None)
            buildMessages.addWarning(textNoAnsi)
          case Info =>
            buildMessages
          case Log =>
            buildMessages
        }
    }

    private def reportDiagnostics(params: PublishDiagnosticsParams): Unit = {
      // TODO use params.requestId to show tree structure

      val file = params.uri.toFile
      params.diagnostics.foreach { diagnostic: Diagnostic =>
        val start = diagnostic.range.start
        val end = diagnostic.range.end
        val position = Some(new FilePosition(file, start.line - 1, start.character, end.line - 1, end.character))
        val text = s"${diagnostic.message} [${start.line}:${start.character}]"

        report.log(text)
        buildMessages = buildMessages.appendMessage(text)

        import ch.epfl.scala.bsp.DiagnosticSeverity._
        buildMessages =
          diagnostic.severity.map {
            case Error =>
              report.error(text, position)
              buildMessages.addError(text)
            case Warning =>
              report.warning(text, position)
              buildMessages.addWarning(text)
            case Information =>
              report.info(text, position)
              buildMessages
            case Hint =>
              report.info(text, position)
              buildMessages
          }
            .getOrElse {
              report.info(text, position)
              buildMessages
            }
      }
    }

    private def reportCompile(compileReport: CompileReport): Unit = {
      // TODO report CompileReport (but might not be really necessary for us)
    }

  }

  private class TextCollector extends ColoredTextAcceptor {
    private val builder = StringBuilder.newBuilder

    override def coloredTextAvailable(text: String, attributes: Key[_]): Unit =
      builder.append(text)

    def result: String = builder.result()
  }

}