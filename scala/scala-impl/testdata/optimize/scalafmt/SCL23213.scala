// Notification message: Removed 2 imports
package example.bug

import com.typesafe.scalalogging.LazyLogging
import zio.*

import java.time.{Duration, Instant}
import javax.crypto.SecretKey
import scala.annotation.nowarn
import scala.annotation.tailrec

object KeyManagementService extends LazyLogging {
  // visible for testing
  @nowarn("cat=lint-infer-any")
  private[encryption] def retrySchedule(
      maxRetryDuration: Duration
  ): Schedule[Any, Any, (Duration, Duration)] = {
    import zio.duration2DurationOps

    (Schedule.exponential(100.millis).jittered(0.0, 1.0) && Schedule.elapsed)
      .whileOutput({ case (nextDelay, duration) =>
        // if the next cycle will put us over the max duration, stop
        (nextDelay + duration) < maxRetryDuration
      })
  }

  @tailrec def foo(n: Int, acc: Int = 1): Int = if (n < 2) acc else foo(n - 1, acc * n)
}
/*package example.bug

import com.typesafe.scalalogging.LazyLogging
import zio.*

import java.time.Duration
import scala.annotation.{nowarn, tailrec}

object KeyManagementService extends LazyLogging {
  // visible for testing
  @nowarn("cat=lint-infer-any")
  private[encryption] def retrySchedule(
      maxRetryDuration: Duration
  ): Schedule[Any, Any, (Duration, Duration)] = {
    import zio.duration2DurationOps

    (Schedule.exponential(100.millis).jittered(0.0, 1.0) && Schedule.elapsed)
      .whileOutput({ case (nextDelay, duration) =>
        // if the next cycle will put us over the max duration, stop
        (nextDelay + duration) < maxRetryDuration
      })
  }

  @tailrec def foo(n: Int, acc: Int = 1): Int = if (n < 2) acc else foo(n - 1, acc * n)
}
*/