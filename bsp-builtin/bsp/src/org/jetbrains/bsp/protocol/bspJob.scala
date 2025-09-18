package org.jetbrains.bsp.protocol

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.bsp.{BspError, BspTaskCancelled}
import org.jetbrains.sbt.shell.CancelableWaitUtil

import scala.annotation.tailrec
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Try}


abstract class BspJob[T] {
  def future: Future[T]
  def cancel(): Unit
}

object BspJob {

  def waitForJobCancelable[R](job: BspJob[R], promise: Promise[_], indicator: ProgressIndicator): Try[R] =
    CancelableWaitUtil.waitForCancelable(
      future = job.future, onCancel = () => job.cancel()
    )(promise, indicator, cancelError = BspTaskCancelled)

  // TODO Consider moving this to `CancelableWaitUtil`
  @tailrec def waitForJob[R](job: BspJob[R], retries: Int): Try[R] =
    try {
      if (retries <= 0) {
        job.cancel()
        Failure(BspTaskCancelled)
      } else {
        val res = Await.result(job.future, 300.millis)
        Try(res)
      }
    } catch {
      case _ : TimeoutException => waitForJob(job, retries)
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
