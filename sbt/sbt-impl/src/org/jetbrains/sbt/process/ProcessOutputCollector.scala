package org.jetbrains.sbt.process

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger

import scala.collection.mutable

final class ProcessOutputCollector:
  // in failed tests we would like to see sbt process output
  val processOutputBuilder: mutable.StringBuilder = new mutable.StringBuilder()

  def processOutput: String = processOutputBuilder.mkString

object ProcessOutputCollector:
  private val Log: Logger = Logger.getInstance(classOf[ProcessOutputCollector])

  private def isUnitTestMode: Boolean =
    ApplicationManager.getApplication.isUnitTestMode

  val PrintProcessOutputOnFailurePropertyName = "sbt.import.print.process.output.on.failure"

  /**
   * Sets up a [[StringBuilder]] for collecting the raw process output such that it can be examined in tests.
   * @return [[Some]] if the process output should be collected, [[None]] otherwise.
   */
  private[sbt] def setUpProcessOutputCollection(): Option[ProcessOutputCollector] =
    val collectProcessOutput = isUnitTestMode ||
      java.lang.Boolean.getBoolean(PrintProcessOutputOnFailurePropertyName)
    Log.debug(s"collectProcessOutput = $collectProcessOutput")
    if (collectProcessOutput) Some(ProcessOutputCollector()) else None
