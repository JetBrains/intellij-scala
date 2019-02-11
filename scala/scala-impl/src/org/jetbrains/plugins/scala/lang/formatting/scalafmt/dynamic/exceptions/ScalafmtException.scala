package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.exceptions

import scala.util.control.NoStackTrace

case class ScalafmtException(message: String, cause: Throwable)
    extends Exception(message, cause)
    with NoStackTrace
