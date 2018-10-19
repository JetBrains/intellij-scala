package org.jetbrains.bsp.protocol

import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

import ch.epfl.scala.bsp
import ch.epfl.scala.bsp.endpoints.{Build, BuildTarget}
import ch.epfl.scala.bsp.{InitializeBuildResult, InitializedBuildParams, Shutdown, endpoints}
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import monix.eval.Task
import monix.execution.{CancelableFuture, Scheduler}
import monix.reactive.Observable
import org.jetbrains.bsp.BspUtil.IdeaLoggerOps
import org.jetbrains.bsp._
import org.jetbrains.bsp.protocol.Bsp4sNotifications._
import org.jetbrains.bsp.protocol.BspCommunication._
import org.jetbrains.bsp.protocol.BspSession._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise, TimeoutException}
import scala.meta.jsonrpc._
import scala.util.control.NonFatal

class BspSession(messages: Observable[BaseProtocolMessage],
                 private implicit val client: LanguageClient,
                 initializeBuildParams: bsp.InitializeBuildParams,
                 cleanup: Task[Unit],
                 private implicit val scheduler: Scheduler
                ) {

  private val logger = Logger.getInstance(classOf[BspCommunication])

  private val jobs = new LinkedBlockingQueue[SessionBspJob[_,_]]

  private var currentJob: SessionBspJob[_,_] = DummyJob

  private val runningClientServer = startClientServer // this future does not complete
  private val sessionInitialized = initializeSession
  private val sessionShutdown = Promise[Unit]

  private val queueProcessor = AppExecutorUtil.getAppScheduledExecutorService
      .scheduleWithFixedDelay(() => nextQueuedCommand, 10, 10, TimeUnit.MILLISECONDS)


  private def nextQueuedCommand= {
    val timeout = 1.second
    try {
      val currentIgnoringErrors = currentJob.future.recover {
        case NonFatal(_) => ()
      }
      val readyForNext = sessionInitialized.flatMap(_ => currentIgnoringErrors)
      Await.result(readyForNext, timeout) // will throw on init/job error
      val next = jobs.poll(timeout.toMillis, TimeUnit.MILLISECONDS)
      if (next != null) {
        currentJob = next
        currentJob.run
      }
    } catch {
      case _: TimeoutException => // just carry on
      case error: BspConnectionError =>
        logger.warn("problem connecting to bsp server", error)
        shutdown(Some(error))
      case NonFatal(error) =>
        logger.error(error)
    }
  }

  private def startClientServer(implicit scheduler: Scheduler): CancelableFuture[Unit] = {
    val services = {
      Services.empty(logger.toScribeLogger)
        .notification(Build.logMessage)(p => currentJob.notification(LogMessage(p)))
        .notification(Build.showMessage)(p => currentJob.notification(ShowMessage(p)))
        .notification(Build.publishDiagnostics)(p => currentJob.notification(PublishDiagnostics(p)))
        .notification(BuildTarget.compileReport)(p => currentJob.notification(CompileReport(p)))
        .notification(BuildTarget.testReport)(p => currentJob.notification(TestReport(p)))
    }
    val server = new LanguageServer(messages, client, services, scheduler, logger.toScribeLogger)
    val serverTask = server.startTask
      .doOnFinish { errOpt =>
        cleanup.map {_ =>
          logger.debug("client/server closed")
          errOpt.foreach { err =>
            logger.warn(s"client/server closed with error: $err")
          }
        }
      }
      .doOnCancel(cleanup)

    serverTask.runAsync
  }

  private def initializeSession: CancelableFuture[InitializeBuildResult] = {
    val startup = for {
      // TODO handle initResult.capabilities
      initialized <- endpoints.Build.initialize.request(initializeBuildParams)
    } yield {
      endpoints.Build.initialized.notify(InitializedBuildParams())
      initialized
    }

    startup
      .flatMap {
        case Left(error) => Task.raiseError(BspConnectionError(error.error.message))
        case Right(result) => Task.now(result)
      }
      .timeoutTo(5.seconds, Task.raiseError(BspConnectionError("bsp server is not responding")))
      .runAsync
  }



  /** Run a task with client in this session.
    * Notifications during run of this task are passed to the aggregator. This can also be used for plain callbacks.
    */
  def run[T, A](task: Bsp4sSessionTask[T], default: A, aggregator: NotificationAggregator[A]): BspJob[(T,A)] = {
    val job = new MonixBspJob(task(client), default, aggregator)
    jobs.put(job)
    job
  }

  def isAlive: Boolean = ! sessionShutdown.isCompleted

  def shutdown(error: Option[BspError] = None): Future[Unit] = {

    val whenDone = {
      val shutdownRequest = for {
        shutdown <- endpoints.Build.shutdown.request(Shutdown())
      } yield {
        shutdown match {
          case Left(Response.Error(err, id)) =>
            BSP.balloonNotification.createNotification(err.message, NotificationType.ERROR)
            val fullMessage = s"${err.message} (code ${err.code}). Data: ${err.data.getOrElse("{}")}"
            logger.error(fullMessage)
          case _ =>
        }
        endpoints.Build.exit.notify(bsp.Exit())
      }

      val cleaning = Task {
        runningClientServer.cancel()
      }

      // TODO check process state, hard-kill bsp process if shutdown was not orderly
      shutdownRequest.flatMap(_ => cleaning).timeoutTo(5.seconds, cleaning)
    }

    error match {
      case None =>
        sessionShutdown.success(())
        currentJob.cancel()
        jobs.forEach(_.cancel())
      case Some(err) =>
        sessionShutdown.failure(err)
        currentJob.cancelWithError(err)
        jobs.forEach(_.cancelWithError(err))
    }
    queueProcessor.cancel(false)
    sessionInitialized.cancel()
    whenDone.runAsync
  }
}

object BspSession {

  private abstract class SessionBspJob[T,A] extends BspJob[(T,A)] {
    private[BspSession] def notification(bspNotification: Bsp4sNotification): Unit
    private[BspSession] def run(implicit scheduler: Scheduler): CancelableFuture[(T, A)]
    private[BspSession] def cancelWithError(error: BspError)
  }

  private class MonixBspJob[T,A](task: Task[T], default: A, aggregator: NotificationAggregator[A]) extends SessionBspJob[T,A] {

    private val promise = Promise[(T,A)]
    private var a: A = default

    private var runningTask: Option[CancelableFuture[(T,A)]] = None

    override private[BspSession] def notification(bspNotification: Bsp4sNotification): Unit =
      a = aggregator(a, bspNotification)

    private def prepare(implicit scheduler: Scheduler): Task[(T,A)] =
      task
        .map { t =>
          val result = (t,a)
          promise.success(result)
          result
        }
        .doOnCancel {
          Task(promise.failure(BspTaskCancelled))
        }
        .doOnFinish {
          case Some(err) => Task(promise.failure(err))
          case None => Task.unit
        }

    private[BspSession] def run(implicit scheduler: Scheduler): CancelableFuture[(T, A)] = runningTask.synchronized {
      runningTask match {
        case Some(running) =>
          running
        case None =>
          val running = prepare.runAsync
          runningTask = Some(running)
          running
      }
    }

    override def future: Future[(T, A)] = promise.future

    override def cancel() : Unit =
      cancelWithError(BspTaskCancelled)

    override def cancelWithError(error: BspError): Unit = runningTask.synchronized {
      runningTask match {
        case Some(toCancel) =>
          toCancel.cancel()
        case None =>
          runningTask = Some(CancelableFuture.failed(error))
          promise.failure(error)
      }
    }
  }

  // TODO barebones handling of logMessage/showMessage?
  private object DummyJob extends SessionBspJob[Unit,Unit] {
    override private[BspSession] def notification(bspNotification: Bsp4sNotification): Unit = ()
    override private[BspSession] def run(implicit scheduler: Scheduler): CancelableFuture[(Unit, Unit)] = CancelableFuture.successful(((),()))
    override private[BspSession] def cancelWithError(error: BspError): Unit = ()
    override def future: Future[(Unit,Unit)] = Future.successful(((),()))
    override def cancel(): Unit = ()
  }
}
