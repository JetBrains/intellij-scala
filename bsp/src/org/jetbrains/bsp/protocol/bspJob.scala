package org.jetbrains.bsp.protocol

import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator}
import org.jetbrains.bsp.BspError

import scala.annotation.tailrec
import scala.concurrent._
import duration._
import scala.util.{Failure, Try}


abstract class BspJob[T] {
  def future: Future[T]
  def cancel(): Unit
}

object BspJob {

  /** Check both indicator and promise for canceled status to combine different ways of canceling tasks.  */
  class CancelCheck(promise: Promise[Unit], indicator: ProgressIndicator) {

    def cancel(): Unit = {
      promise.failure(new ProcessCanceledException())
      indicator.cancel()
    }


    def complete(): Unit = {
      promise.success(())
      // let's just assume indicator stop is handled elsewhere
    }

    def isCancelled: Boolean = {
      // if one is canceled, cancel the other
      if (!promise.isCompleted && indicator.isCanceled)
        promise.failure(new ProcessCanceledException())
      else if (!indicator.isCanceled && promise.isCompleted)
        indicator.cancel()

      promise.isCompleted
    }
  }

  @tailrec def waitForJobCancelable[R](job: BspJob[R], cancelCheck: CancelCheck): Try[R] =
    try {
      if (cancelCheck.isCancelled) {
        job.cancel()
        Failure(new ProcessCanceledException())
      } else {
        val res = Await.result(job.future, 300.millis)
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
