package org.jetbrains.bsp.protocol.session

import java.util.concurrent.CompletableFuture

/**
 * JDK's CompletableFutre does not handle cancellation well.
 * When a future does have an overriden `cancel` method, it is lost
 * when doing transformations like `thenApply` or `thenCompose`.
 *
 * In Java 9, the new CompletableFuture's method was introduced: `newIncompleteFuture`.
 * When using it, the cancel method of the original future will be called event when the
 * `cancel` is done on a transformed one.
 *
 * When we know about CompletableFutures that override `cancel` methods, we should convert them
 * to CancellableFuture before calling methods like `thenCompose`
 *
 * @param original
 */
class CancellableFuture[T](original: CompletableFuture[_]) extends CompletableFuture[T] {
  override def cancel(mayInterruptIfRunning: Boolean): Boolean = {
    original.cancel(mayInterruptIfRunning)
    super.cancel(mayInterruptIfRunning)
  }

  override def newIncompleteFuture[U](): CompletableFuture[U] = {
    new CancellableFuture(original)
  }
}

object CancellableFuture {
  def from[T](original: CompletableFuture[T]): CancellableFuture[T] = {
    val result = new CancellableFuture[T](original)
    original.whenComplete{ (value, error) =>
      if (error != null) {
        result.completeExceptionally(error)
      }
      else {
        result.complete(value)
      }
      ()
    }
    result
  }
}