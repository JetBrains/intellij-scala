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
import mercator._
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.project.BspTask.{BspTarget, TextCollector}
import org.jetbrains.bsp.protocol.session.BspSession.{BspServer, NotificationAggregator, ProcessLogger}
import org.jetbrains.bsp.protocol.{BspCommunication, BspJob, BspNotifications}
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildToolWindowReporter, IndicatorReporter}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, TimeoutException}
import scala.util.control.NonFatal
import scala.util.{Failure, Try}

class BspTask[T](project: Project,
                 targets: Iterable[BspTarget],
                 targetsToClean: Iterable[BspTarget],
                 callbackOpt: Option[ProjectTaskNotification],
                 onComplete: ()=>Unit)
    extends Task.Backgroundable(project, "bsp build", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {

  private val bspTaskId: EventId = BuildMessages.randomEventId
  private val report = new BuildToolWindowReporter(project, bspTaskId, "bsp build")

  private var diagnostics: mutable.Map[URI, List[Diagnostic]] = mutable.Map.empty

  import BspNotifications._
  private def notifications(report: BuildToolWindowReporter): NotificationAggregator[BuildMessages] =
    (messages, notification) => notification match {
    case LogMessage(params) =>
      report.log(params.getMessage)
      messages
    case ShowMessage(params) =>
      reportShowMessage(messages, params)
    case PublishDiagnostics(params) =>
      reportDiagnostics(messages, params)
    case TaskStart(params) =>
      reportTaskStart(params)
      messages
    case TaskProgress(params) =>
      reportTaskProgress(params)
      messages
    case TaskFinish(params) =>
      reportTaskFinish(params)
      messages
    case DidChangeBuildTarget(_) =>
      // ignore
      messages
  }

  private def processLog(report: BuildToolWindowReporter): ProcessLogger = { message =>
    report.log(message)
  }

  override def run(indicator: ProgressIndicator): Unit = {
    val reportIndicator = new IndicatorReporter(indicator)

    val targetByWorkspace = targets.groupBy(_.workspace)
    val targetToCleanByWorkspace = targetsToClean.groupBy(_.workspace)

    reportIndicator.start()
    report.start()

    val buildJobs = targetByWorkspace.map { case (workspace, targets) =>
      val targetsToClean = targetToCleanByWorkspace.getOrElse(workspace, List.empty)
      val communication: BspCommunication = BspCommunication.forWorkspace(workspace.toFile)
      communication.run(
        buildRequests(targets, targetsToClean)(_,_),
        BuildMessages.empty,
        notifications(report),
        processLog(report))
    }

    val combinedMessages = buildJobs
      .traverse(waitForJobCancelable(_, indicator))
      .map { compileResults =>
        val updatedMessages = compileResults.map(r => messagesWithStatus(report, reportIndicator, r._1, r._2))
        updatedMessages.fold(BuildMessages.empty) { (m1, m2) => m1.combine(m2) }
      }.recover {
        case _: ProcessCanceledException =>
          BuildMessages.empty.status(BuildMessages.Canceled)
        case NonFatal(x: Exception) =>
          BuildMessages.empty
            .status(BuildMessages.Error)
            .exception(x)
      }
      .getOrElse{
        BuildMessages.empty
          .status(BuildMessages.Error)
          .addError(s"Build failed: unknown reason")
      }


    // TODO start/finish task for individual builds
    if (combinedMessages.status == BuildMessages.Canceled) {
      report.finishCanceled()
      reportIndicator.finishCanceled()
    } else if (combinedMessages.exceptions.nonEmpty) {
      // TODO report all exceptions?
      report.finishWithFailure(combinedMessages.exceptions.head)
      reportIndicator.finishWithFailure(combinedMessages.exceptions.head)
    }
    else {
      report.finish(combinedMessages)
      reportIndicator.finish(combinedMessages)
    }

    val result = combinedMessages.toTaskResult

    callbackOpt.foreach(_.finished(result))
    onComplete()
  }

  private def messagesWithStatus(report: BuildToolWindowReporter,
                               reportIndicator: IndicatorReporter,
                               result: CompileResult,
                               messages: BuildMessages): BuildMessages = {
    try {
      result.getStatusCode match {
        case StatusCode.OK =>
          messages.status(BuildMessages.OK)
        case StatusCode.ERROR =>
          messages.status(BuildMessages.Error)
        case StatusCode.CANCELLED =>
          messages.status(BuildMessages.Canceled)
      }

    } catch {
      case error: ResponseErrorException =>
        val msg = error.getMessage
        // TODO report full error to log?
        report.error(msg, None)
        reportIndicator.error(msg, None)

        messages
          .addError(msg)
          .status(BuildMessages.Error)

      case _: ProcessCanceledException =>
        messages.status(BuildMessages.Canceled)

      case NonFatal(err) =>
        val errName = err.getClass.getName
        val msg = Option(err.getMessage).getOrElse(errName)
        // TODO report full error to log?
        report.error(msg, None)
        messages
          .addError(msg)
          .status(BuildMessages.Error)
    }
  }


  @tailrec private def waitForJobCancelable[R](job: BspJob[R], indicator: ProgressIndicator): Try[R] =
    try {
      indicator.checkCanceled()
      val res = Await.result(job.future, 300.millis)
      Try(res)
    } catch {
      case _ : TimeoutException => waitForJobCancelable(job, indicator)
      case cancel : ProcessCanceledException =>
        job.cancel()
        Failure(cancel)
    }

  private def buildRequests(targets: Iterable[BspTarget], targetsToClean: Iterable[BspTarget])
                           (implicit server: BspServer, capabilities: BuildServerCapabilities) = {
    if (targetsToClean.isEmpty) compileRequest(targets)
    else {
      cleanRequest(targetsToClean)
      .exceptionally { err =>
        new CleanCacheResult(s"server does not support cleaning build cache (${err.getMessage})", false)
      }
      .thenCompose { cleaned =>
        if (cleaned.getCleaned) compileRequest(targets)
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

  private def cleanRequest(targetsToClean: Iterable[BspTarget])
                          (implicit server: BspServer, capabilities: BuildServerCapabilities): CompletableFuture[CleanCacheResult] = {
    val targetIds = targetsToClean.map(target => new bsp4j.BuildTargetIdentifier(target.target.toString))
    val params = new bsp4j.CleanCacheParams(targetIds.toList.asJava)
    server.buildTargetCleanCache(params)
  }

  private def compileRequest(targets: Iterable[BspTarget])
                            (implicit server: BspServer, capabilities: BuildServerCapabilities): CompletableFuture[CompileResult] = {
    val targetIds = targets.map(target => new bsp4j.BuildTargetIdentifier(target.target.toString))
    val params = new bsp4j.CompileParams(targetIds.toList.asJava)
    params.setOriginId(bspTaskId.id)

    server.buildTargetCompile(params)
  }

  private def reportShowMessage(buildMessages: BuildMessages, params: bsp4j.ShowMessageParams): BuildMessages = {
    // TODO handle message type (warning, error etc) in output
    // TODO use params.requestId to show tree structure
    val text = params.getMessage
    report.log(text)

    // TODO build toolwindow log supports ansi colors, but not some other stuff
    val textNoAnsiAcceptor = new TextCollector
    new AnsiEscapeDecoder().escapeText(text, ProcessOutputTypes.STDOUT, textNoAnsiAcceptor)
    val textNoAnsi = textNoAnsiAcceptor.result

    import bsp4j.MessageType._
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

  private def reportDiagnostics(buildMessages: BuildMessages, params: bsp4j.PublishDiagnosticsParams): BuildMessages = {
    // TODO use params.originId to show tree structure

    val uri = params.getTextDocument.getUri.toURI
    val uriDiagnostics = params.getDiagnostics.asScala
    val previousDiagnostics = diagnostics.getOrElse(uri, List.empty)
    diagnostics.put(uri, uriDiagnostics.toList)

    uriDiagnostics
      .filterNot(previousDiagnostics.contains)
      .foldLeft(buildMessages) { (messages, diagnostic) =>

      val start = diagnostic.getRange.getStart
      val end = diagnostic.getRange.getEnd
      val position = Some(new FilePosition(uri.toFile, start.getLine, start.getCharacter, end.getLine, end.getCharacter))
      val text = s"${diagnostic.getMessage} [${start.getLine + 1}:${start.getCharacter + 1}]"

      import bsp4j.DiagnosticSeverity._
      Option(diagnostic.getSeverity).map {
        case ERROR =>
          report.error(text, position)
          messages.addError(text)
        case WARNING =>
          report.warning(text, position)
          messages.addWarning(text)
        case INFORMATION =>
          report.info(text, position)
          messages
        case HINT =>
          report.info(text, position)
          messages
      }
        .getOrElse {
          report.info(text, position)
          messages
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

  case class BspTarget(workspace: URI, target: URI)
}
