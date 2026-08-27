package org.jetbrains.plugins.scala.util

import com.intellij.openapi.progress.{CoroutinesKt, ProgressIndicator}
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

import kotlin.coroutines.Continuation
import kotlinx.coroutines.{BuildersKt, Dispatchers}

object ProgressUtils {
  /**
   * Runs `action` under a thread-bound [[ProgressIndicator]] so that synchronous bridges that use
   * `runBlockingCancellable` find an enclosing job/indicator and don't trip the "no ProgressIndicator
   * or Job in this thread" error.
   */
  @RequiresBackgroundThread
  def runUnderProgress[T](action: ProgressIndicator => T): T = {
    BuildersKt.runBlocking(
      Dispatchers.getDefault,
      (_, cont: Continuation[? >: T]) => CoroutinesKt.coroutineToIndicator(indicator => action(indicator), cont)
    )
  }
}
