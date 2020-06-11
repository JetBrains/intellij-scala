package org.jetbrains.bsp
package protocol
package session

import java.util.concurrent.CompletableFuture

/**
 * JDK's CompletableFuture does not handle cancellation well.
 * When a future overrides a `cancel` method, it is lost
 * when doing transformations like `thenApply` or `thenCompose`.
 *
 * When we know about CompletableFutures that override `cancel` methods, we should convert them
 * to CancellableFuture before calling methods like `thenCompose`
 */
final class CancellableFuture[T](delegateFuture: CompletableFuture[T])
  extends CompletableFuture[T] {

  delegateFuture.whenComplete {
    case (value, null) => complete(value)
    case (_, ex) => completeExceptionally(ex)
  }

  override def cancel(mayInterruptIfRunning: Boolean): Boolean = {
    delegateFuture.cancel(mayInterruptIfRunning)
    super.cancel(mayInterruptIfRunning)
  }

  /**
   * * In Java 9, the new CompletableFuture's method was introduced: `newIncompleteFuture`.
   * * When using it, the cancel method of the original future will be called event when the
   * * `cancel` is done on a transformed one.
   */
  //override def newIncompleteFuture[U] = new CancellableFuture(delegateFuture)
}
