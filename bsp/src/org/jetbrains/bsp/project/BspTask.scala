package org.jetbrains.bsp.project

import java.util.UUID

import ch.epfl.scala.bsp.{BuildTargetIdentifier, _}
import com.intellij.build.FilePosition
import com.intellij.execution.process.AnsiEscapeDecoder.ColoredTextAcceptor
import com.intellij.execution.process.{AnsiEscapeDecoder, ProcessOutputTypes}
import com.intellij.openapi.progress.{PerformInBackgroundOption, ProgressIndicator, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.task.{ProjectTaskNotification, _}
import monix.eval
import monix.execution.Scheduler
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.project.BspTask.TextCollector
import org.jetbrains.bsp.protocol.BspCommunication
import org.jetbrains.bsp.protocol.BspCommunication.NotificationCallback
import org.jetbrains.bsp.settings.BspExecutionSettings
import org.jetbrains.plugins.scala.build.{BuildFailureException, BuildMessages, BuildToolWindowReporter, IndicatorReporter}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.meta.jsonrpc.{LanguageClient, Response}
import scala.util.control.NonFatal

class BspTask[T](project: Project, executionSettings: BspExecutionSettings,
                   targets: List[BuildTargetIdentifier], callbackOpt: Option[ProjectTaskNotification])
                  (implicit scheduler: Scheduler)
    extends Task.Backgroundable(project, "bsp build", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {

  private var buildMessages: BuildMessages = BuildMessages.empty

  private val taskId: UUID = UUID.randomUUID()
  private val report = new BuildToolWindowReporter(project, taskId, "bsp build")

  private val bspNotifications: NotificationCallback = {
    case BspCommunication.LogMessage(params) =>
      report.log(params.message)
    case BspCommunication.ShowMessage(params) =>
      reportShowMessage(params)
    case BspCommunication.PublishDiagnostics(params) =>
      reportDiagnostics(params)
    case BspCommunication.CompileReport(params) =>
      reportCompile(params)

  }

  override def run(indicator: ProgressIndicator): Unit = {
    val reportIndicator = new IndicatorReporter(indicator)

    val communication = BspCommunication.forProject(project)
    val buildTask = communication.run(compileRequest(_), bspNotifications)

    reportIndicator.start()
    report.start()

    val projectTaskResult = try {
      val result = Await.result(buildTask, Duration.Inf)
      result match {
        case Left(error) =>
          val message = error.toBspError.getMessage
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

  private def compileRequest(implicit client: LanguageClient): eval.Task[Either[Response.Error, CompileResult]] =
    endpoints.BuildTarget.compile.request(CompileParams(targets, None, List.empty)) // TODO support requestId

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
    // TODO use CompileReport to signal individual target is completed
  }
}

object BspTask {
  private class TextCollector extends ColoredTextAcceptor {
    private val builder = StringBuilder.newBuilder

    override def coloredTextAvailable(text: String, attributes: Key[_]): Unit =
      builder.append(text)

    def result: String = builder.result()
  }
}