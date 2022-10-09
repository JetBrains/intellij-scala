package org.jetbrains.plugins.scala.testingSupport.test.utils

import com.intellij.execution.process.{ProcessAdapter, ProcessEvent, ProcessHandler}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.scala.testingSupport.test.utils.RawProcessOutputDebugLogger.Log

final class RawProcessOutputDebugLogger extends ProcessAdapter {

  override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
    val eventText = event.getText
    if (eventText.trim.nonEmpty) {
      val message = s"[$outputType] $eventText"
      Log.info(message)
    }
  }
}

object RawProcessOutputDebugLogger {

  private val Log = Logger.getInstance(classOf[RawProcessOutputDebugLogger])

  // set to true to debug raw process output, can be useful to test teamcity service messages
  private def enabled: Boolean =
    Registry.get("scala.test.framework.runner.log.raw.process.output").asBoolean()

  def maybeAddListenerTo(processHandler: ProcessHandler): Unit = {
    if (enabled) {
      addListenerTo(processHandler)
    }
  }

  def addListenerTo(processHandler: ProcessHandler): Unit =
    processHandler.addProcessListener(new RawProcessOutputDebugLogger)
}