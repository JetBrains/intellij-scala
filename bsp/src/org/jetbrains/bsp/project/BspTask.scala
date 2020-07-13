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
import mercator._
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.jetbrains.bsp.BspBundle
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.project.BspTask.{BspTarget, TextCollector}
import org.jetbrains.bsp.protocol.BspJob.CancelCheck
import org.jetbrains.bsp.protocol.session.BspSession.{BspServer, NotificationAggregator, ProcessLogger}
import org.jetbrains.bsp.protocol.{BspCommunication, BspJob, BspNotifications}
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.BuildToolWindowReporter.CancelBuildAction
import org.jetbrains.plugins.scala.build._
import org.jetbrains.plugins.scala.util.CompilationId

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

class BspTask[T](project: Project,
                 targets: Iterable[BspTarget],
                 targetsToClean: Iterable[BspTarget]
                )
    extends Task.Backgroundable(project, BspBundle.message("bsp.task.build"), true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {

  private val bspTaskId: EventId = BuildMessages.randomEventId
  private val resultPromise: Promise[BuildMessages] = Promise()

  private val diagnostics: mutable.Map[URI, List[Diagnostic]] = mutable.Map.empty

  import BspNotifications._
  private def notifications(implicit reporter: BuildReporter): NotificationAggregator[BuildMessages] =
    (messages, notification) => notification match {
    case LogMessage(params) =>
      //noinspection ReferencePassedToNls
      reporter.log(params.getMessage)
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

  private def processLog(implicit report: BuildReporter): ProcessLogger = { message =>
    //noinspection ReferencePassedToNls
    report.log(message)
  }

  def resultFuture: Future[BuildMessages] = resultPromise.future

  override def onThrowable(error: Throwable): Unit = {
    resultPromise.failure(error)
  }

  override def onCancel(): Unit = {
    resultPromise.tryFailure(new ProcessCanceledException())
  }

  override def run(indicator: ProgressIndicator): Unit = {
    implicit val reporter: BuildReporter = new CompositeReporter(
      new BuildToolWindowReporter(project, bspTaskId, BspBundle.message("bsp.task.build"), new CancelBuildAction(resultPromise)),
      new CompilerEventReporter(project, CompilationId.generate()),
      new IndicatorReporter(indicator)
    )

    val targetByWorkspace = targets.groupBy(_.workspace)
    val targetToCleanByWorkspace = targetsToClean.groupBy(_.workspace)

    reporter.start()

    val buildJobs = targetByWorkspace.map { case (workspace, targets) =>
      val targetsToClean = targetToCleanByWorkspace.getOrElse(workspace, List.empty)
      val communication: BspCommunication = BspCommunication.forWorkspace(workspace.toFile)
      communication.run(
        buildRequests(targets, targetsToClean)(_, _, reporter),
        BuildMessages.empty,
        notifications,
        processLog)
    }

    val cancelToken = new CancelCheck(resultPromise, indicator)

    val combinedMessages = buildJobs
      .traverse(BspJob.waitForJobCancelable(_, cancelToken))
      .map { compileResults =>
        val updatedMessages = compileResults.map(r => messagesWithStatus(reporter, r._1, r._2))
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
          .addError(BspBundle.message("bsp.task.build.failed.unknown.reason"))
      }


    // TODO start/finish task for individual builds
    if (combinedMessages.status == BuildMessages.Canceled) {
      reporter.finishCanceled()
    } else if (combinedMessages.exceptions.nonEmpty) {
      // TODO report all exceptions?
      reporter.finishWithFailure(combinedMessages.exceptions.head)
    }
    else reporter.finish(combinedMessages)

    resultPromise.trySuccess(combinedMessages)
  }

  private def messagesWithStatus(reporter: BuildReporter,
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
        reporter.error(msg, None)

        messages
          .addError(msg)
          .status(BuildMessages.Error)

      case _: ProcessCanceledException =>
        messages.status(BuildMessages.Canceled)

      case NonFatal(err) =>
        val errName = err.getClass.getName
        val msg = Option(err.getMessage).getOrElse(errName)
        // TODO report full error to log?
        reporter.error(msg, None)
        messages
          .addError(msg)
          .status(BuildMessages.Error)
    }
  }

  private def buildRequests(targets: Iterable[BspTarget], targetsToClean: Iterable[BspTarget])
                           (implicit server: BspServer, capabilities: BuildServerCapabilities, reporter: BuildReporter) = {
    if (targetsToClean.isEmpty) compileRequest(targets)
    else {
      cleanRequest(targetsToClean)
      .exceptionally { err =>
        new CleanCacheResult(BspBundle.message("bsp.task.server.does.not.support.cleaning.build.cache", err.getMessage), false)
      }
      .thenCompose { cleaned =>
        if (cleaned.getCleaned) compileRequest(targets)
        else {
          reporter.error(BspBundle.message("bsp.task.targets.not.cleaned", cleaned.getMessage), None)
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

  private def reportShowMessage(buildMessages: BuildMessages, params: bsp4j.ShowMessageParams)(implicit reporter: BuildReporter): BuildMessages = {
    // TODO handle message type (warning, error etc) in output
    // TODO use params.requestId to show tree structure
    val text = params.getMessage
    reporter.log(text)

    // TODO build toolwindow log supports ansi colors, but not some other stuff
    val textNoAnsiAcceptor = new TextCollector
    new AnsiEscapeDecoder().escapeText(text, ProcessOutputTypes.STDOUT, textNoAnsiAcceptor)
    val textNoAnsi = textNoAnsiAcceptor.result

    import bsp4j.MessageType._
    params.getType match {
      case ERROR =>
        reporter.error(textNoAnsi, None)
        buildMessages.addError(textNoAnsi)
      case WARNING =>
        reporter.warning(textNoAnsi, None)
        buildMessages.addWarning(textNoAnsi)
      case INFORMATION =>
        buildMessages
      case LOG =>
        buildMessages
    }
  }

  //noinspection ReferencePassedToNls
  private def reportDiagnostics(buildMessages: BuildMessages, params: bsp4j.PublishDiagnosticsParams)
                               (implicit reporter: BuildReporter): BuildMessages = {
    // TODO use params.originId to show tree structure

    val uri = params.getTextDocument.getUri.toURI
    val uriDiagnostics = params.getDiagnostics.asScala
    val previousDiagnostics = diagnostics.getOrElse(uri, List.empty)
    diagnostics.put(uri, uriDiagnostics.toList)

    if (uriDiagnostics.isEmpty) {
      reporter.clear(uri.toFile)
      buildMessages
    } else
      uriDiagnostics
        .filterNot(previousDiagnostics.contains)
        .foldLeft(buildMessages) { (messages, diagnostic) =>

          val start = diagnostic.getRange.getStart
          val end = diagnostic.getRange.getEnd
          val position = Some(new FilePosition(uri.toFile, start.getLine, start.getCharacter, end.getLine, end.getCharacter))
          val text = s"${diagnostic.getMessage} [${start.getLine + 1}:${start.getCharacter + 1}]"

          import bsp4j.DiagnosticSeverity._
          Option(diagnostic.getSeverity)
            .map {
              case ERROR =>
                reporter.error(text, position)
                messages.addError(text)
              case WARNING =>
                reporter.warning(text, position)
                messages.addWarning(text)
              case INFORMATION =>
                reporter.info(text, position)
                messages
              case HINT =>
                reporter.info(text, position)
                messages
            }
            .getOrElse {
              reporter.info(text, position)
              messages
            }
        }
  }

  //noinspection ReferencePassedToNls
  private def reportTaskStart(params: TaskStartParams)(implicit reporter: BuildReporter): Unit = {
    val taskId = params.getTaskId
    val id = EventId(taskId.getId)
    val parent = Option(taskId.getParents).flatMap(_.asScala.headOption).map(EventId).orElse(Option(bspTaskId))
    val time = Option(params.getEventTime.longValue()).getOrElse(System.currentTimeMillis())
    reporter.startTask(id, parent, params.getMessage, time)
  }

  //noinspection ReferencePassedToNls
  private def reportTaskProgress(params: TaskProgressParams)(implicit reporter: BuildReporter): Unit = {
    val taskId = params.getTaskId
    val id = EventId(taskId.getId)
    val time = Option(params.getEventTime.longValue()).getOrElse(System.currentTimeMillis())
    reporter.progressTask(id, params.getTotal, params.getProgress, params.getUnit, params.getMessage, time)
  }

  //noinspection ReferencePassedToNls
  private def reportTaskFinish(params: TaskFinishParams)(implicit reporter: BuildReporter): Unit = {
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
        new FailureResultImpl(BspBundle.message("bsp.task.unknown.status.code", otherCode), null)
    }

    reporter.finishTask(id, params.getMessage, result, time)
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
