package org.jetbrains.plugins.scala.util

import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator}

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise, TimeoutException}
import scala.util.{Failure, Try}

object CancelableWaitUtil {
  /**
   * Waits for the given future to complete. 
   * The method periodically (every ~300 ms) checks whether either:
   *   - the `resultPromise` has been completed, or
   *   - the `indicator` has been canceled.
   *   
   * If any of the above happens, `onCancel` is called and a Failure with `cancelError` is returned.
   *
   * @param future the computation to wait for
   * @param onCancel a callback invoked once when cancellation is detected
   * @param resultPromise a promise whose completion indicates external cancellation
   */
  def waitForCancelable[R](
    future: Future[R], 
    onCancel: () => Unit
  )(resultPromise: Promise[_], indicator: ProgressIndicator, cancelError: Throwable = defaultCancelError): Try[R] = {
    val cancelCheck = new CancelCheck(resultPromise, indicator, cancelError)
    
    @tailrec
    def wait(): Try[R] = {
      try {
        if (cancelCheck.isCancelled) {
          onCancel()
          Failure(cancelCheck.cancelError)
        } else {
          val res = Await.result(future, 300.millis)
          Try(res)
        }
      } catch {
        case _: TimeoutException => wait()
      }
    }
    
    wait()
  }

  private val defaultCancelError = new ProcessCanceledException()

  private class CancelCheck(val promise: Promise[_], val indicator: ProgressIndicator, val cancelError: Throwable = defaultCancelError) {
    def isCancelled: Boolean = {
      // if one is canceled, cancel the other
      if (!promise.isCompleted && indicator.isCanceled)
        promise.failure(cancelError)
      else if (!indicator.isCanceled && promise.isCompleted)
        indicator.cancel()

      promise.isCompleted
    }
  }
}
