package org.jetbrains.bsp

trait BspError extends Exception
case class BspErrorMessage(message: String) extends Exception(message) with BspError
case class BspException(message: String, cause: Throwable) extends Exception(message, cause) with BspError
case class BspConnectionError(message: String) extends Exception(message) with BspError
case object BspTaskCancelled extends Exception("bsp task was cancelled") with BspError
