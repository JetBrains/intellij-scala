package org.jetbrains.plugins.scala.util

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.concurrent.duration._

object FutureUtil {

  @tailrec
  def executeIfTimeout(future: Future[_],
                       timeout: FiniteDuration,
                       blockDuration: FiniteDuration = DefaultBlockDuration)
                      (onTimeout: => Unit): Unit =
    if (timeout <= Duration.Zero) {
      onTimeout
    } else if (!future.isCompleted) {
      Thread.sleep(blockDuration.toMillis)
      executeIfTimeout(future, timeout - blockDuration, blockDuration)(onTimeout)
    }
  
  private val DefaultBlockDuration: FiniteDuration = 50.millis
}
