package org.jetbrains.bsp.protocol

import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

import ch.epfl.scala.bsp
import ch.epfl.scala.bsp.endpoints.{Build, BuildTarget}
import ch.epfl.scala.bsp.{InitializeBuildResult, InitializedBuildParams, Shutdown, endpoints}
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import monix.eval.Task
import monix.execution.{CancelableFuture, Scheduler}
import monix.reactive.Observable
import org.jetbrains.bsp.BspUtil.IdeaLoggerOps
import org.jetbrains.bsp.protocol.BspCommunication._
import org.jetbrains.bsp.protocol.BspSession._
import org.jetbrains.bsp.{BSP, BspTaskCancelled}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise, TimeoutException}
import scala.meta.jsonrpc._

class BspSession(messages: Observable[BaseProtocolMessage],
                 private implicit val client: LanguageClient,
                 initializeBuildParams: bsp.InitializeBuildParams,
                 cleanup: Task[Unit],
                 private implicit val scheduler: Scheduler
                ) {

  private val logger = Logger.getInstance(classOf[BspCommunication])

  private val jobs = new LinkedBlockingQueue[BspJob[_,_]]

  private var currentJob: BspJob[_,_] = DummyJob

  private val runningClientServer = startClientServer // this future does not complete
  private val sessionInitialized: CancelableFuture[Either[Response.Error, InitializeBuildResult]] = initializeSession

  private val queueProcessor = scheduler.scheduleWithFixedDelay(0,0, TimeUnit.SECONDS, () => nextQueuedCommand)

  private def nextQueuedCommand= {
    val timeout = 1.second
    try {
      Await.ready(sessionInitialized.map(_ => currentJob.future), timeout)
      val next = jobs.poll(timeout.toMillis, TimeUnit.SECONDS)
      if (next != null) {
        currentJob = next
        currentJob.prepare.runAsync
      }
    } catch {
      case _: TimeoutException => // just carry on
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
        for {
          cleaned <- cleanup
        } yield {
          logger.debug("client/server closed")
          errOpt.foreach { err =>
            logger.warn(s"client/server closed with error: $err")
          }
        }
      }
      .doOnCancel(cleanup)

    serverTask.runAsync
  }

  private def initializeSession: CancelableFuture[Either[Response.Error, InitializeBuildResult]] = {
    val startup = for {
      // TODO handle initResult.capabilities
      initialized <- endpoints.Build.initialize.request(initializeBuildParams)
    } yield {
      endpoints.Build.initialized.notify(InitializedBuildParams())
      initialized
    }

    startup.runAsync
  }



  /** Run a task with client in this session.
    * Notifications during run of this task are passed to the aggregator. This can also be used for plain callbacks.
    */
  def run[T, A](task: BspSessionTask[T], default: A, aggregator: NotificationAggregator[A]): Future[(T,A)] = {
    val job = BspJob(task(client), default, aggregator)
    jobs.put(job)
    job.future
  }


  def shutdown(): CancelableFuture[Unit] = {

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
        logger.debug("closing bsp connection")
        runningClientServer.cancel()
      }

      for {
        _ <- shutdownRequest
        _ <- cleaning
        // TODO check process state, hard-kill bsp process if shutdown was not orderly
      } yield ()
    }

    queueProcessor.cancel()
    currentJob.cancel()
    jobs.forEach(_.cancel())
    whenDone.runAsync
  }

}

object BspSession {

  private case class BspJob[T,A](task: Task[T], default: A, aggregator: NotificationAggregator[A]) {

    private val promise = Promise[(T,A)]
    private var a: A = default

    def notification(bspNotification: BspNotification): Unit =
      a = aggregator(a, bspNotification)

    def prepare(implicit scheduler: Scheduler): Task[(T,A)] =
      task
        .map { t =>
          val result = (t,a)
          promise.success(result)
          result
        }
      .doOnCancel { Task(promise.failure(BspTaskCancelled)) }
      .doOnFinish {
        case Some(err) => Task(promise.failure(err))
        case None => Task.unit
      }

    def future: Future[(T, A)] = promise.future

    def cancel() : Unit = () // TODO task cancellation
  }

  // TODO barebones handling of logMessage/showMessage?
  private object DummyJob extends BspJob[Unit,Unit](Task.unit, (), (_,_) => ()) {
    private implicit val scheduler: Scheduler = Scheduler.global
    prepare.runAsync
  }
}
