package org.jetbrains.plugins.scala.actions.utils

import com.intellij.openapi.editor.event.{VisibleAreaEvent, VisibleAreaListener}
import org.jetbrains.annotations.NotNull
import org.jetbrains.concurrency.CancellablePromise

import java.util.concurrent.atomic.AtomicReference

/**
 * Originally copied and converted to Scala from com.intellij.codeInsight.hint.CancelProgressOnScrolling (it's package private and inaccessible)
 * No changes behavioral changes made in the initial commit.
 */
final class CancelProgressOnScrolling private[utils](private val myCancellablePromiseRef: AtomicReference[_ <: CancellablePromise[_]])
  extends VisibleAreaListener {

  override def visibleAreaChanged(@NotNull e: VisibleAreaEvent): Unit = {
    val oldRect = e.getOldRectangle
    val newRect = e.getNewRectangle
    val promise = myCancellablePromiseRef.get
    val needToCancel = oldRect != null && (oldRect.x != newRect.x || oldRect.y != newRect.y) && promise != null
    if (needToCancel) {
      promise.cancel()
    }
  }
}