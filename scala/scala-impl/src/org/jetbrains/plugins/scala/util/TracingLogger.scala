package org.jetbrains.plugins.scala.util

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.util.TracingLogger.LogLevel

final class TracingLogger(private val logger: Logger, level: LogLevel) {

  def log(message: String): Unit = log(message, TracingLogger.DefaultTraceLogLevel)

  def log(message: String, level: LogLevel): Unit = level match {
    case LogLevel.Info  => logger.info(message)
    case LogLevel.Debug => logger.debug(message)
    case LogLevel.Trace => logger.trace(message)
    case _              =>
  }
}

object TracingLogger {

  private val Log: Logger = Logger.getInstance("ScalaMethodCallTracing")

  private val DefaultTraceLogLevel: LogLevel = Option(System.getProperty("scala.method.trace.log.level")).map(_.toLowerCase) match {
    case Some("info")  => LogLevel.Info
    case Some("debug") => LogLevel.Debug
    case Some("trace") => LogLevel.Trace
    case Some("none")  => LogLevel.None
    case _             => LogLevel.Debug
  }

  def apply(): TracingLogger = new TracingLogger(Log, DefaultTraceLogLevel)

  private sealed trait LogLevel

  private object LogLevel {
    object Info extends LogLevel
    object Debug extends LogLevel
    object Trace extends LogLevel
    object None extends LogLevel
  }
}