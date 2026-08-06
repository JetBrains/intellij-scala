package org.jetbrains.plugins.scala
package util

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.annotations.ApiStatus

import scala.concurrent.duration.{Duration, DurationInt}

/**
 * @note Extra methods like `_Seconds` added for convenience to avoid importing scala.concurrent.duration.DurationInt at the usage site
 */
//noinspection ScalaUnusedSymbol
@ApiStatus.Internal
@ApiStatus.Experimental
object InternalDevUtils {

  def runDummyComputationWithProgressCancellationCheck_Seconds(totalDurationSeconds: Int): Unit = {
    runDummyComputationWithProgressCancellationCheck(totalDurationSeconds.seconds)
  }

  def runDummyComputationWithProgressCancellationCheck_Millis(totalDurationMillis: Int): Unit = {
    runDummyComputationWithProgressCancellationCheck(totalDurationMillis.millis)
  }


  /**
   * A simple helper method that can emulate long computations (like type inference and resolve)
   * with timely `ProgressManager.checkCanceled()` invocations.
   * It's supposed to be primarily used during the local development.
   *
   * ATTENTION: BE CAREFUL NOT TO USE IT IN PRODUCTION CODE!
   */
  def runDummyComputationWithProgressCancellationCheck(totalDuration: Duration): Unit = {
    val startTime = System.currentTimeMillis()
    while (System.currentTimeMillis() - startTime < totalDuration.toMillis) {
      // Some syntactical CPU computation
      (1 to 100000).map(x => Math.log(x))

      try ProgressManager.checkCanceled() catch {
        case t: Throwable =>
          throw t
      }
    }
  }
}
