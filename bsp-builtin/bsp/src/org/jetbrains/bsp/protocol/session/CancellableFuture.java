package org.jetbrains.bsp.protocol.session;

import java.util.concurrent.CompletableFuture;

/**
 * JDK's CompletableFuture does not handle cancellation well.
 * When a future overrides a `cancel` method, it is lost
 * when doing transformations like `thenApply` or `thenCompose`.
 *
 * In Java 9, the new CompletableFuture's method was introduced: `newIncompleteFuture`.
 * When using it, the cancel method of the original future will be called event when the
 * `cancel` is done on a transformed one.
 *
 * When we know about CompletableFutures that override `cancel` methods, we should convert them
 * to CancellableFuture before calling methods like `thenCompose`
 *
 * The `newIncompleteFuture` is not annotated with @Override, so the code can be compiled with both JVM8 and JVM9
 * (and newer), but the actuall cancellation effect takes place only with 9 or newer.
 */
public class CancellableFuture<T> extends CompletableFuture<T> {

    final private CompletableFuture<?> original;

    public CancellableFuture(CompletableFuture<?> original)
    {
        this.original = original;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        original.cancel(mayInterruptIfRunning);
        return super.cancel(mayInterruptIfRunning);
    }

    @Override
    public <U> CompletableFuture<U> newIncompleteFuture() {
        return new CancellableFuture<>(original);
    }

    public static <U> CancellableFuture<U> from(CompletableFuture<U> original) {
        CancellableFuture<U> result = new CancellableFuture<>(original);
        original.whenComplete((value, error) -> {
            if(error != null) {
                result.completeExceptionally(error);
            } else {
                result.complete(value);
            }
        });
        return result;
    }
}

