package org.jetbrains.bsp.project

import java.net.URI
import java.util.concurrent.CompletableFuture

import ch.epfl.scala.bsp4j
import ch.epfl.scala.bsp4j._
import com.intellij.build.FilePosition
import com.intellij.build.events.impl.{FailureResultImpl, SkippedResultImpl, SuccessResultImpl}
import com.intellij.execution.process.AnsiEscapeDecoder.ColoredTextAcceptor
import com.intellij.execution.process.{AnsiEscapeDecoder, ProcessOutputTypes}
import com.intellij.openapi.progress.{PerformInBackgroundOption, ProcessCanceledException, ProgressIndicator, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.task.ProjectTaskNotification
import org.eclipse.lsp4j.jsonrpc.{CompletableFutures, ResponseErrorException}
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.project.BspTask.TextCollector
import org.jetbrains.bsp.protocol.session.BspSession.{BspServer, NotificationCallback, ProcessLogger}
import org.jetbrains.bsp.protocol.{BspCommunication, BspJob, BspNotifications}
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildFailureException, BuildMessages, BuildToolWindowReporter, IndicatorReporter}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, TimeoutException}
import scala.util.control.NonFatal

class BspTask[T](project: Project, targets: Iterable[URI], targetsToClean: Iterable[URI], callbackOpt: Option[ProjectTaskNotification], onComplete: ()=>Unit)
    extends Task.Backgroundable(project, "bsp build", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {

  private var buildMessages: BuildMessages = BuildMessages.empty

  private val bspTaskId: EventId = BuildMessages.randomEventId
  private val report = new BuildToolWindowReporter(project, bspTaskId, "bsp build")

  private var diagnostics: mutable.Map[URI, List[Diagnostic]] = mutable.Map.empty

  import BspNotifications._
  private val notifications: NotificationCallback = {
    case LogMessage(params) =>
      report.log(params.getMessage)
    case ShowMessage(params) =>
      reportShowMessage(params)
    case PublishDiagnostics(params) =>
      reportDiagnostics(params)
    case TaskStart(params) =>
      reportTaskStart(params)
    case TaskProgress(params) =>
      reportTaskProgress(params)
    case TaskFinish(params) =>
      reportTaskFinish(params)
    case DidChangeBuildTarget(_) =>
      // ignore
  }

  private val processLog: ProcessLogger = { message =>
    report.log(message)
  }

  override def run(indicator: ProgressIndicator): Unit = {
    val reportIndicator = new IndicatorReporter(indicator)

    val communication = BspCommunication.forProject(project)

    reportIndicator.start()
    report.start()

    val buildJob = communication.run(buildRequests, notifications, processLog)
    val projectTaskResult = try {
      val result = waitForJobCancelable(buildJob, indicator)
      buildMessages = result.getStatusCode match {
        case StatusCode.OK =>
          buildMessages.status(BuildMessages.OK)
        case StatusCode.ERROR =>
          buildMessages.status(BuildMessages.Error)
        case StatusCode.CANCELLED =>
          buildMessages.status(BuildMessages.Canceled)
      }

      // TODO report language specific compile result if available
      report.finish(buildMessages)
      reportIndicator.finish(buildMessages)
      buildMessages.toTaskResult

    } catch {
      case error: ResponseErrorException =>
        buildMessages = buildMessages.status(BuildMessages.Error)
        val message = error.getMessage
        val failure = BuildFailureException(message)
        report.error(message, None)
        report.finishWithFailure(failure)
        reportIndicator.finishWithFailure(failure)
        buildMessages.toTaskResult

      case _: ProcessCanceledException =>
        report.finishCanceled()
        reportIndicator.finishCanceled()
        buildMessages = buildMessages.status(BuildMessages.Canceled)
        buildMessages.toTaskResult

      case NonFatal(err) =>
        val errName = err.getClass.getName
        val msg = Option(err.getMessage).getOrElse(errName)
        report.error(msg, None)
        report.finishWithFailure(err)
        reportIndicator.finishWithFailure(err)
        buildMessages = buildMessages.status(BuildMessages.Error)
        buildMessages.toTaskResult
    }

    callbackOpt.foreach(_.finished(projectTaskResult))
    onComplete()
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

  private def buildRequests(server: BspServer) = {
    if (targetsToClean.isEmpty) compileRequest(server)
    else {
      cleanRequest(server)
      .exceptionally { err =>
        new CleanCacheResult(s"server does not support cleaning build cache (${err.getMessage})", false)
      }
      .thenCompose { cleaned =>
        if (cleaned.getCleaned) compileRequest(server)
        else {
          report.error("targets not cleaned, rebuild cancelled: " + cleaned.getMessage, None)
          val res = new CompileResult(StatusCode.CANCELLED)
          val future = new CompletableFuture[CompileResult]()
          future.complete(res)
          future
        }
      }
    }
  }

  private def cleanRequest(server: BspServer): CompletableFuture[CleanCacheResult] = {
    val targetIds = targetsToClean.map(uri => new bsp4j.BuildTargetIdentifier(uri.toString))
    val params = new bsp4j.CleanCacheParams(targetIds.toList.asJava)
    server.buildTargetCleanCache(params)
  }

  private def compileRequest(server: BspServer): CompletableFuture[CompileResult] = {
    val targetIds = targets.map(uri => new bsp4j.BuildTargetIdentifier(uri.toString))
    val params = new bsp4j.CompileParams(targetIds.toList.asJava)
    params.setOriginId(bspTaskId.id)

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

    val uri = params.getTextDocument.getUri.toURI
    val uriDiagnostics = params.getDiagnostics.asScala
    val previousDiagnostics = diagnostics.getOrElse(uri, List.empty)
    diagnostics.put(uri, uriDiagnostics.toList)

    uriDiagnostics
      .filterNot(previousDiagnostics.contains)
      .foreach { diagnostic: bsp4j.Diagnostic =>

      val start = diagnostic.getRange.getStart
      val end = diagnostic.getRange.getEnd
      val position = Some(new FilePosition(uri.toFile, start.getLine, start.getCharacter, end.getLine, end.getCharacter))
      val text = s"${diagnostic.getMessage} [${start.getLine + 1}:${start.getCharacter + 1}]"

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

  private def reportTaskStart(params: TaskStartParams): Unit = {
    val taskId = params.getTaskId
    val id = EventId(taskId.getId)
    val parent = Option(taskId.getParents).flatMap(_.asScala.headOption).map(EventId).orElse(Option(bspTaskId))
    val time = Option(params.getEventTime.longValue()).getOrElse(System.currentTimeMillis())
    report.startTask(id, parent, params.getMessage, time)
  }

  private def reportTaskProgress(params: TaskProgressParams): Unit = {
    val taskId = params.getTaskId
    val id = EventId(taskId.getId)
    val time = Option(params.getEventTime.longValue()).getOrElse(System.currentTimeMillis())
    report.progressTask(id, params.getTotal, params.getProgress, params.getUnit, params.getMessage, time)
  }

  private def reportTaskFinish(params: TaskFinishParams): Unit = {
    val taskId = params.getTaskId
    val id = EventId(taskId.getId)
    val time = Option(params.getEventTime.longValue()).getOrElse(System.currentTimeMillis())
    val result = params.getStatus match {
      case StatusCode.OK =>
        new SuccessResultImpl()
      case StatusCode.CANCELLED =>
        new SkippedResultImpl
      case StatusCode.ERROR =>
        new FailureResultImpl(params.getMessage, null)
      case otherCode =>
        new FailureResultImpl(s"unknown status code $otherCode", null)
    }

    report.finishTask(id, params.getMessage, result, time)
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
