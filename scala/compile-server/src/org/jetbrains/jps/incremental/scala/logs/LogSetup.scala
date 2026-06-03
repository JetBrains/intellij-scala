package org.jetbrains.jps.incremental.scala.logs

import com.intellij.openapi.diagnostic.{JulLogger, LogLevel, Logger}
import org.jetbrains.plugins.scala.server.{CompileServerLog, CompileServerProperties}

import java.io.IOException
import java.nio.file.Paths

/**
 * Similar to `org.jetbrains.jps.cmdline.LogSetup` which cannot itself be directly used, as it is compiled with JDK 11.
 * The compile server must be able to run on JDK 8.
 */
//noinspection ApiStatus,UnstableApiUsage
object LogSetup {
  def initLoggers(): Unit = {
    try {
      val logDir = sys.props.get(CompileServerProperties.LogDirectory) match {
        case Some(dir) => Paths.get(dir)
        case None => return
      }
      val logFilePath = CompileServerLog.logFilePath(logDir)
      JulLogger.clearHandlers()
      JulLogger.configureStandardLoggers(
        /* consoleLogLevel   = */ LogLevel.OFF,
        /* showDateInConsole = */ true,
        /* logFilePath       = */ logFilePath,
        /* appendToFile      = */ true,
        /* writeAttachments  = */ false,
        /* onRotate          = */ null
      )
    } catch {
      case e: IOException =>
        Console.err.println("Failed to configure logging: ")
        e.printStackTrace(Console.err)
    }

    Logger.setFactory(category => new JulLogger(java.util.logging.Logger.getLogger(category)))
  }
}
