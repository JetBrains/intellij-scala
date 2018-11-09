package org.jetbrains.bsp.project

import java.net.URI
import java.util.Collections
import java.util.concurrent.CompletableFuture

import ch.epfl.scala.bsp4j
import ch.epfl.scala.bsp4j.CompileResult
import com.google.gson.JsonArray
import com.intellij.build.FilePosition
import com.intellij.build.events.Failure
import com.intellij.build.events.impl.{FailureResultImpl, SuccessResultImpl}
import com.intellij.execution.process.AnsiEscapeDecoder.ColoredTextAcceptor
import com.intellij.execution.process.{AnsiEscapeDecoder, ProcessOutputTypes}
import com.intellij.openapi.progress.{PerformInBackgroundOption, ProcessCanceledException, ProgressIndicator, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.task.{ProjectTaskNotification, _}
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.project.BspTask.TextCollector
import org.jetbrains.bsp.protocol.BspSession.{BspServer, NotificationCallback}
import org.jetbrains.bsp.protocol.{BspCommunication, BspJob, BspNotifications}
import org.jetbrains.bsp.settings.BspExecutionSettings
import org.jetbrains.plugins.scala.build.BuildMessages.{EventId, StringId}
import org.jetbrains.plugins.scala.build.{BuildFailureException, BuildMessages, BuildToolWindowReporter, IndicatorReporter}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, TimeoutException}
import scala.util.control.NonFatal

class BspTask[T](project: Project, executionSettings: BspExecutionSettings,
                   targets: Iterable[URI], callbackOpt: Option[ProjectTaskNotification])
    extends Task.Backgroundable(project, "bsp build", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {

  private var buildMessages: BuildMessages = BuildMessages.empty

  private val taskId: EventId = BuildMessages.randomEventId
  private val report = new BuildToolWindowReporter(project, taskId, "bsp build")

  private val notifications: NotificationCallback = {
    case BspNotifications.LogMessage(params) =>
      report.log(params.getMessage)
    case BspNotifications.ShowMessage(params) =>
      reportShowMessage(params)
    case BspNotifications.PublishDiagnostics(params) =>
      reportDiagnostics(params)
    case BspNotifications.CompileReport(params) =>
      reportCompile(params)
    case BspNotifications.TestReport(params) =>
      // ignore in compile tasks
  }

  override def run(indicator: ProgressIndicator): Unit = {
    val reportIndicator = new IndicatorReporter(indicator)

    val communication = BspCommunication.forProject(project)

    reportIndicator.start()
    report.start()

    val buildJob = communication.run(compileRequest(_), notifications)
    val projectTaskResult = try {
      val result = waitForJobCancelable(buildJob, indicator)

      // TODO report language specific compile result if available
      report.finish(buildMessages)
      reportIndicator.finish(buildMessages)
      new ProjectTaskResult(buildMessages.aborted, buildMessages.errors.size, buildMessages.warnings.size)

    } catch {
      case error: ResponseErrorException =>
        val message = error.getMessage
        val failure = BuildFailureException(message)
        report.error(message, None)
        report.finishWithFailure(failure)
        reportIndicator.finishWithFailure(failure)
        new ProjectTaskResult(true, buildMessages.errors.size, buildMessages.warnings.size)

      case _: ProcessCanceledException =>
        report.finishCanceled()
        reportIndicator.finishCanceled()
        new ProjectTaskResult(true, 1, 0)

      case NonFatal(err) =>
        val errName = err.getClass.getName
        val msg = Option(err.getMessage).getOrElse(errName)
        report.error(msg, None)
        report.finishWithFailure(err)
        reportIndicator.finishWithFailure(err)
        new ProjectTaskResult(true, 1, 0)
    }

    callbackOpt.foreach(_.finished(projectTaskResult))
  }

  @tailrec private def waitForJobCancelable[R](job: BspJob[R], indicator: ProgressIndicator): R = {
    try {
      indicator.checkCanceled()
      Await.result(job.future, 300.millis)
    } catch {
      case _ : TimeoutException => waitForJobCancelable(job, indicator)
      case cancel : ProcessCanceledException =>
        job.cancel()
        throw cancel
    }
  }

  private def compileRequest(implicit server: BspServer): CompletableFuture[CompileResult] = {
    val targetIds = targets.map(uri => new bsp4j.BuildTargetIdentifier(uri.toString))
    val params = new bsp4j.CompileParams(targetIds.toList.asJava)
    params.setOriginId(taskId.toString)

    server.buildTargetCompile(params)
  }

  private def reportShowMessage(params: bsp4j.ShowMessageParams): Unit = {
    // TODO handle message type (warning, error etc) in output
    // TODO use params.requestId to show tree structure
    val text = params.getMessage
    report.log(text)

    // TODO build toolwindow log supports ansi colors, but not some other stuff
    val textNoAnsiAcceptor = new TextCollector
    new AnsiEscapeDecoder().escapeText(text, ProcessOutputTypes.STDOUT, textNoAnsiAcceptor)
    val textNoAnsi = textNoAnsiAcceptor.result

    buildMessages = buildMessages.appendMessage(textNoAnsi)

    import bsp4j.MessageType._
    buildMessages =
      params.getType match {
        case ERROR =>
          report.error(textNoAnsi, None)
          buildMessages.addError(textNoAnsi)
        case WARNING =>
          report.warning(textNoAnsi, None)
          buildMessages.addWarning(textNoAnsi)
        case INFORMATION =>
          buildMessages
        case LOG =>
          buildMessages
      }
  }

  private def reportDiagnostics(params: bsp4j.PublishDiagnosticsParams): Unit = {
    // TODO use params.originId to show tree structure

    val file = params.getTextDocument.getUri.toURI.toFile
    params.getDiagnostics.asScala.foreach { diagnostic: bsp4j.Diagnostic =>
      val start = diagnostic.getRange.getStart
      val end = diagnostic.getRange.getEnd
      val position = Some(new FilePosition(file, start.getLine - 1, start.getCharacter, end.getLine - 1, end.getCharacter))
      val text = s"${diagnostic.getMessage} [${start.getLine}:${start.getCharacter}]"

      report.log(text)
      buildMessages = buildMessages.appendMessage(text)

      import bsp4j.DiagnosticSeverity._
      buildMessages =
        Option(diagnostic.getSeverity).map {
          case ERROR =>
            report.error(text, position)
            buildMessages.addError(text)
          case WARNING =>
            report.warning(text, position)
            buildMessages.addWarning(text)
          case INFORMATION =>
            report.info(text, position)
            buildMessages
          case HINT =>
            report.info(text, position)
            buildMessages
        }
          .getOrElse {
            report.info(text, position)
            buildMessages
          }
    }
  }

  /** Report compilation stats and success/failure of an individual target.
    * TODO requires notifications of compile task start
    */
  private def reportCompile(compileReport: bsp4j.CompileReport): Unit = {
    val targetUri = compileReport.getTarget.getUri
    val taskId = StringId(targetUri)
    val warnings = compileReport.getWarnings
    val errors = compileReport.getErrors

    val (message,result) = if (errors > 0) {
      val warningsMsg = if (warnings>0) s", $warnings warnings"
      val msg = s"failed with $errors errors$warningsMsg"
      val result = new SuccessResultImpl(true)
      (msg,result)
    }
    else {
      val warningsMsg = if (warnings>0) s": $warnings warnings" else ""
      val msg = s"completed$warningsMsg"
      val result = new FailureResultImpl(Collections.emptyList[Failure]())
      (msg,result)
    }
    val fullMessage = s"$targetUri $message"

    report.finishTask(taskId, message, result)
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
