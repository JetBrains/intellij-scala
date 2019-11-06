package org.jetbrains.bsp

trait BspError extends Exception
case class BspErrorMessage(message: String) extends Exception(message) with BspError
case class BspException(message: String, cause: Throwable) extends Exception(message, cause) with BspError
case class BspConnectionError(message: String, cause: Throwable = null) extends Exception(message, cause) with BspError
case object BspSessionClosed extends Exception("BSP session was closed") with BspError
case object BspTaskCancelled extends Exception("BSP task was cancelled") with BspError
