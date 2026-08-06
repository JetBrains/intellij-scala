package org.jetbrains.bsp.protocol.session

import org.jetbrains.bsp.BspTaskCancelled

import java.util.concurrent.{CancellationException, CompletionException, ExecutionException}
import scala.annotation.tailrec

private[session] object BspJavaFutureFailure {

  /**
   * Removes the standard exception wrappers used by Java futures.
   *
   * [[java.util.concurrent.CompletableFuture]] stores failures as completion results.
   * When a failed future is observed through another future stage, Java may expose the failure as a [[CompletionException]] whose cause is the real error.
   * When the same failure is observed through blocking `get`, Java exposes it as an [[ExecutionException]].
   *
   * These wrappers can also be nested, for example, when `get` observes a future that is already completed with a `CompletionException`.
   *
   * This method recurses only through those future wrappers, so callers can classify the actual BSP cancellation/error at the leaf.
   * It is intentionally not a generic root-cause search:
   * unrelated domain exceptions can have meaningful causes, and those causes should not silently change how the top-level failure is handled.
   *
   * @note a similar approach is used in [[com.intellij.debugger.impl.DebuggerUtilsAsync#unwrap]]
   */
  @tailrec
  def unwrap(error: Throwable): Throwable = {
    val cause = error.getCause
    error match {
      case _: CompletionException | _: ExecutionException if cause != null && (cause ne error) =>
        unwrap(cause)
      case _ =>
        error
    }
  }

  /**
   * Recognizes cancellation at Java-future boundaries after removing only Java future wrappers.
   */
  def isCancellation(error: Throwable): Boolean = {
    val unwrappedException = unwrap(error)
    unwrappedException match {
      case BspTaskCancelled | _: CancellationException => true
      case _ => false
    }
  }
}
