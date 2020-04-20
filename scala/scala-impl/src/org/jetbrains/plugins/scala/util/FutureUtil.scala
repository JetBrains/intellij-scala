package org.jetbrains.plugins.scala.util

import scala.concurrent.ExecutionContext

object FutureUtil {
  
  implicit val sameThreadExecutionContext: ExecutionContext =
    ExecutionContext.fromExecutor(_.run())
}
