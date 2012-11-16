package org.jetbrains.jps.incremental.scala

import xsbti.{Logger, F0}
import org.jetbrains.jps.incremental.MessageHandler
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind

/**
* @author Pavel Fatin
*/
class MessageHandlerLogger(compilerName: String, messageHandler: MessageHandler) extends Logger {
  def error(msg: F0[String]) {
    messageHandler.processMessage(new CompilerMessage(compilerName, Kind.ERROR, msg()))
  }

  def warn(msg: F0[String]) {
    messageHandler.processMessage(new CompilerMessage(compilerName, Kind.WARNING, msg()))
  }

  def info(msg: F0[String]) {
    messageHandler.processMessage(new CompilerMessage(compilerName, Kind.INFO, msg()))
  }

  def debug(msg: F0[String]) {
    messageHandler.processMessage(new CompilerMessage(compilerName, Kind.INFO, msg()))
  }

  def trace(exception: F0[Throwable]) {
    messageHandler.processMessage(new CompilerMessage(compilerName, exception()))
  }
}
