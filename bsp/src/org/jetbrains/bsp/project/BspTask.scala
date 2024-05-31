package org.jetbrains.bsp.project

import ch.epfl.scala.bsp4j
import ch.epfl.scala.bsp4j._
import com.intellij.build.FilePosition
import com.intellij.build.events.impl.{FailureResultImpl, SkippedResultImpl, SuccessResultImpl}
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, Task}
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.project.BspTask.BspTarget
import org.jetbrains.bsp.protocol.BspJob.CancelCheck
import org.jetbrains.bsp.protocol.session.BspSession.{BspServer, NotificationAggregator, ProcessLogger}
import org.jetbrains.bsp.protocol.{BspCommunication, BspJob, BspNotifications}
import org.jetbrains.bsp.{BSP, BspBundle}
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.BuildToolWindowReporter.CancelBuildAction
import org.jetbrains.plugins.scala.build._
import org.jetbrains.plugins.scala.util.{CompilationId, ExternalSystemVfsUtil}

import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{Future, Promise}
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.control.NonFatal

class BspTask[T](project: Project,
                 targets: Iterable[BspTarget],
                 targetsToClean: Iterable[BspTarget],
                 arguments: Option[CustomTaskArguments]
                )
    extends Task.Backgroundable(
      project,
      arguments.map(_.message).getOrElse(BspBundle.message("bsp.task.build")),
      true
    ) {

  private val bspTaskId: EventId = BuildMessages.randomEventId
  private val resultPromise: Promise[BuildMessages] = Promise()

  private val diagnostics: collection.concurrent.TrieMap[URI, Vector[Diagnostic]] = collection.concurrent.TrieMap.empty

  /*
   * Mill-specific fix. For some reason, Mill sends two TaskFinish messages. We only want to report diagnostics once.
   */
  private val taskFinished: AtomicBoolean = new AtomicBoolean()

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
      collectDiagnostics(params)
      messages
    case TaskStart(params) =>
      reportTaskStart(params)
      messages
    case TaskProgress(params) =>
      reportTaskProgress(params)
      messages
    case TaskFinish(params) =>
      reportTaskFinish(messages, params)
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
    implicit val reporter: BuildReporter =
      arguments.map(_.reporter)
        .getOrElse {
          new CompositeReporter(
            new BuildToolWindowReporter(project, bspTaskId, BspBundle.message("bsp.task.build"), new CancelBuildAction(resultPromise)),
            new CompilerEventReporter(project, CompilationId.generate()),
            new IndicatorReporter(indicator)
          )
        }

    val targetByWorkspace = targets.groupBy(_.workspace)
    val targetToCleanByWorkspace = targetsToClean.groupBy(_.workspace)

    reporter.start()

    val buildJobs = targetByWorkspace.map { case (workspace, targets) =>
      val targetsToClean = targetToCleanByWorkspace.getOrElse(workspace, List.empty)
      val communication: BspCommunication = BspCommunication.forWorkspace(workspace.toFile, project)
      communication.run(
        { case (server, serverInfo) => buildRequests(targets, targetsToClean)(server, serverInfo.capabilities, reporter) },
        BuildMessages.empty,
        notifications,
        processLog
      )
    }

    val cancelToken = new CancelCheck(resultPromise, indicator)

    import BspTask.TryTraversableOps
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

    ExternalSystemVfsUtil.refreshRoots(project, BSP.ProjectSystemId, indicator)
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
    val textNoAnsi = BuildMessages.stripAnsiCodes(text)

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

  private def collectDiagnostics(params: bsp4j.PublishDiagnosticsParams): Unit = {
    val reset = params.getReset
    val uri = params.getTextDocument.getUri.toURI
    val newDiagnostics = params.getDiagnostics.asScala.toVector
    diagnostics.updateWith(uri) {
      case Some(old) if !reset => Some(old ++ newDiagnostics)
      case _ => Some(newDiagnostics)
    }
  }

  //noinspection ReferencePassedToNls
  private def aggregateDiagnostics(buildMessages: BuildMessages)(implicit reporter: BuildReporter): BuildMessages = {
    // TODO use params.originId to show tree structure

    diagnostics
      .foldLeft(buildMessages) { case (messages, (uri, uriDiagnostics)) =>
        if (uriDiagnostics.isEmpty) {
          reporter.clear(uri.toFile)
          messages
        } else {
          uriDiagnostics
            .distinct
            .foldLeft(messages) { case (messages, diagnostic) =>
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
      }
  }

  //noinspection ReferencePassedToNls
  private def reportTaskStart(params: TaskStartParams)(implicit reporter: BuildReporter): Unit = {
    val taskId = params.getTaskId
    val parent = Option(taskId.getParents).flatMap(_.asScala.headOption).map(EventId).orElse(Option(bspTaskId))
    val id = EventId(taskId.getId)
    val time = Option(params.getEventTime.longValue()).getOrElse(System.currentTimeMillis())
    val msg = Option(params.getMessage).getOrElse("")
    reporter.startTask(id, parent, msg, time)
  }

  //noinspection ReferencePassedToNls
  private def reportTaskProgress(params: TaskProgressParams)(implicit reporter: BuildReporter): Unit = {
    val taskId = params.getTaskId
    val id = EventId(taskId.getId)
    val time = Option(params.getEventTime.longValue()).getOrElse(System.currentTimeMillis())
    val msg = Option(params.getMessage).getOrElse("")
    reporter.progressTask(id, params.getTotal, params.getProgress, params.getUnit, msg, time)
  }

  //noinspection ReferencePassedToNls
  private def reportTaskFinish(buildMessages: BuildMessages, params: TaskFinishParams)(implicit reporter: BuildReporter): BuildMessages = {
    if (taskFinished.compareAndSet(false, true)) {
      val diagnostics = aggregateDiagnostics(buildMessages)

      val taskId = params.getTaskId
      val id = EventId(taskId.getId)
      val time = Option(params.getEventTime.longValue()).getOrElse(System.currentTimeMillis())
      val msg = Option(params.getMessage).getOrElse("")

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

      reporter.finishTask(id, msg, result, time)

      diagnostics
    } else {
      buildMessages
    }
  }
}

object BspTask {

  case class BspTarget(workspace: URI, target: URI)

  private final implicit class TryTraversableOps[A](private val ts: Iterable[A]) extends AnyVal {
    def traverse[B](f: A => Try[B]): Try[Iterable[B]] = {
      ts.iterator.foldLeft(Try(Vector.empty[B])) { case (acc, a) =>
        acc.flatMap { xs => f(a).map(xs :+ _) }
      }
    }
  }
}
