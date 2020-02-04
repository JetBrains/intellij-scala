package org.jetbrains.bsp.protocol

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.bsp.{BspError, BspTaskCancelled}

import scala.annotation.tailrec
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Try}


abstract class BspJob[T] {
  def future: Future[T]
  def cancel(): Unit
}

object BspJob {

  /** Check both indicator and promise for canceled status to combine different ways of canceling tasks.  */
  class CancelCheck(promise: Promise[Unit], indicator: ProgressIndicator) {

    def cancel(): Unit = {
      promise.failure(BspTaskCancelled)
      indicator.cancel()
    }


    def complete(): Unit = {
      promise.success(())
      // let's just assume indicator stop is handled elsewhere
    }

    def isCancelled: Boolean = {
      // if one is canceled, cancel the other
      if (!promise.isCompleted && indicator.isCanceled)
        promise.failure(BspTaskCancelled)
      else if (!indicator.isCanceled && promise.isCompleted)
        indicator.cancel()

      promise.isCompleted
    }
  }

  @tailrec def waitForJobCancelable[R](job: BspJob[R], cancelCheck: CancelCheck): Try[R] =
    try {
      if (cancelCheck.isCancelled) {
        job.cancel()
        Failure(BspTaskCancelled)
      } else {
        val res = Await.result(job.future, 300.millis)
        cancelCheck.complete()
        Try(res)
      }
    } catch {
      case _ : TimeoutException => waitForJobCancelable(job, cancelCheck)
    }
}

class NonAggregatingBspJob[T,A](job: BspJob[(T,A)]) extends BspJob[T] {
  override def future: Future[T] = job.future.map(_._1)(ExecutionContext.global)
  override def cancel(): Unit = job.cancel()
}

class FailedBspJob[T](error: BspError) extends BspJob[T] {
  override def future: Future[T] = Future.failed(error)
  override def cancel(): Unit = ()
}
