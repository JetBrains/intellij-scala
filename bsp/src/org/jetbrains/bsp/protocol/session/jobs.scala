package org.jetbrains.bsp.protocol.session

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

import ch.epfl.scala.bsp4j.BuildServerCapabilities
import org.jetbrains.annotations.Nls
import org.jetbrains.bsp.protocol.BspJob
import org.jetbrains.bsp.protocol.BspNotifications.BspNotification
import org.jetbrains.bsp.protocol.session.BspSession.{BspServer, BspSessionTask, NotificationAggregator, ProcessLogger}
import org.jetbrains.bsp.protocol.session.jobs.BspSessionJob
import org.jetbrains.bsp.{BspError, BspTaskCancelled}

import scala.concurrent.{CancellationException, Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

object jobs {

  def create[T,A](task: BspSessionTask[T],
                  default: A,
                  aggregator: NotificationAggregator[A],
                  processLogger: ProcessLogger): BspSessionJob[T,A] = {

    new Bsp4jJob(task, default, aggregator, processLogger)
  }

  private[protocol] abstract class BspSessionJob[T,A] extends BspJob[(T,A)] {

    /** Log message to job--specific logging function. */
    private[protocol] def log(@Nls message: String): Unit

    /** Invoke a bsp notification aggregator or callback with given notification.
      * The result of aggregated notifications is the result value of the future returned by `run`.
      */
    private[session] def notification(bspNotification: BspNotification): Unit

    /** Starts the job with given bspServer instance.
      * Idempotent: returns the same CompletableFuture if invoked more than once.
      */
    private[session] def run(bspServer: BspServer, capabilities: BuildServerCapabilities): CompletableFuture[(T, A)]

    /** Cancel and abort this job with given error. */
    private[session] def cancelWithError(error: BspError): Unit
  }
}

private[session] class FailedBspSessionJob[T,A](problem: BspError) extends BspSessionJob[T,A] {

  override private[protocol] def log(@Nls message: String): Unit = ()
  override private[session] def notification(bspNotification: BspNotification): Unit = ()
  override private[session] def run(bspServer: BspServer, capabilities: BuildServerCapabilities): CompletableFuture[(T, A)] = {
    val cf = new CompletableFuture[(T, A)]()
    cf.completeExceptionally(problem)
    cf
  }
  override private[session] def cancelWithError(error: BspError): Unit = ()
  override def future: Future[(T, A)] = Future.failed(problem)
  override def cancel(): Unit = ()

}

case class Bsp4JJobFailure[A](error: Throwable, messages: A) extends Throwable

private[session] class Bsp4jJob[T,A](task: BspSessionTask[T],
                                     default: A,
                                     aggregator: NotificationAggregator[A],
                                     processLogger: ProcessLogger)
  extends BspSessionJob[T,A] {

  private val promise = Promise[(T,A)]()
  private var a: A = default

  private val runningTask: AtomicReference[Option[CompletableFuture[(T,A)]]] = new AtomicReference(None)

  override private[session] def notification(bspNotification: BspNotification): Unit = {
    a = aggregator(a, bspNotification)
  }

  override private[protocol] def log(message: String): Unit = {
    processLogger(message)
  }

  private def doRun(bspServer: BspServer, capabilities: BuildServerCapabilities): CompletableFuture[(T,A)] = {
    task(bspServer, capabilities).thenApply[(T,A)]((t:T) => (t,a))
      .whenComplete((result: (T,A), error: Throwable) => {
        if (error != null) error match {
          case cancel: CancellationException =>
            promise.failure(BspTaskCancelled)
            throw BspTaskCancelled
          case otherError => promise.failure(otherError)
        } else {
          promise.success(result)
        }
      })
  }

  override private[session] def run(bspServer: BspServer, capabilities: BuildServerCapabilities): CompletableFuture[(T, A)] =
    runningTask.synchronized {
      runningTask.get match {
        case Some(running) =>
          running
        case None =>
          val running = doRun(bspServer, capabilities)
          runningTask.set(Some(running))
          running
      }
    }

  override def future: Future[(T, A)] = promise.future.recoverWith{
    case err  => Future.failed(Bsp4JJobFailure(err, a))
  }

  override def cancel() : Unit =
    if (! promise.isCompleted)
      cancelWithError(BspTaskCancelled)

  override def cancelWithError(error: BspError): Unit = runningTask.synchronized {
    runningTask.get() match {
      case Some(toCancel) =>
        toCancel.cancel(true)
      case None =>
        val errorFuture = new CompletableFuture[(T,A)]
        errorFuture.completeExceptionally(error)
        runningTask.set(Some(errorFuture))
    }

    promise.tryFailure(error)
  }
}


private[session] object DummyJob extends BspSessionJob[Unit,Unit] {
  override private[protocol] def log(message: String): Unit = ()
  override private[session] def notification(bspNotification: BspNotification): Unit = ()
  override private[session] def run(bspServer: BspServer, capabilities: BuildServerCapabilities): CompletableFuture[(Unit, Unit)] =
    CompletableFuture.completedFuture(((),()))
  override private[session] def cancelWithError(error: BspError): Unit = ()
  override def future: Future[(Unit,Unit)] = Future.successful(((),()))
  override def cancel(): Unit = ()
}
