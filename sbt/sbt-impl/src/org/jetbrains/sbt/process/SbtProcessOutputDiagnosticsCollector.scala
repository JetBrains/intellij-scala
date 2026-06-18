package org.jetbrains.sbt.process

import com.intellij.execution.process.{ProcessEvent, ProcessHandler, ProcessListener}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.TestOnly
import org.jetbrains.sbt.process.SbtProcessOutputDiagnosticsCollector.ProcessOutputCollectingListener

import java.util.concurrent.ConcurrentSkipListMap
import scala.jdk.CollectionConverters.*

/**
 * Collects raw sbt process output for diagnostics that are only needed in tests or in explicitly requested debug runs.
 *
 * Instances of this class are used by sbt import code that needs an isolated output buffer for a single structure-dump attempt.
 * For broader test diagnostics, use the companion object methods that attach directly to low-level process handlers and expose a shared test buffer.
 *
 * ATTENTION: the shared test buffer is keyed by human-readable process titles.<br>
 * If several processes with the same title run at the same time, their diagnostics can be lost or interleaved.
 * This is enough for our current tests, where only one observed sbt process is run at a time;
 * in principle, this can be improved in the future with per-run keys or listener cleanup.
 *
 * TODO: the shared collector is unbounded and can retain output across the whole test JVM.
 *  Add a bounded buffer or an explicit shared reset hook if this becomes noisy in tests.
 */
final class SbtProcessOutputDiagnosticsCollector {
  private val collectedProcessOutputByTitle = new ConcurrentSkipListMap[String, StringBuffer]

  def collectProcessOutputFrom(processHandler: ProcessHandler, processTitle: String): Unit = {
    clear(processTitle)

    val listener = ProcessOutputCollectingListener(processTitle, this)
    processHandler.addProcessListener(listener)
  }

  def append(processTitle: String, text: String): Unit =
    if (text.nonEmpty) {
      val processOutput = collectedProcessOutputByTitle.computeIfAbsent(processTitle, _ => new StringBuffer)
      processOutput.append(text)
    }

  def clear(): Unit =
    collectedProcessOutputByTitle.clear()

  private def clear(processTitle: String): Unit =
    collectedProcessOutputByTitle.remove(processTitle)

  def processOutput: String =
    collectedProcessOutputByTitle.entrySet().asScala
      .map { entry =>
        val processOutput = entry.getValue.synchronized {
          entry.getValue.toString
        }
        s"${entry.getKey}:\n$processOutput"
      }
      .mkString("\n")
}

object SbtProcessOutputDiagnosticsCollector {
  private val Log: Logger = Logger.getInstance(classOf[SbtProcessOutputDiagnosticsCollector])
  private val SharedCollector = new SbtProcessOutputDiagnosticsCollector

  val PrintProcessOutputOnFailurePropertyName = "sbt.import.print.process.output.on.failure"

  private[sbt] def createIfEnabled(): Option[SbtProcessOutputDiagnosticsCollector] =
    Log.debug(s"collectProcessOutput = $isProcessOutputCollectionEnabled")
    if (isProcessOutputCollectionEnabled) Some(new SbtProcessOutputDiagnosticsCollector) else None

  private def sharedIfEnabled: Option[SbtProcessOutputDiagnosticsCollector] =
    if (isProcessOutputCollectionEnabled) Some(SharedCollector) else None

  @TestOnly
  private[sbt] def sharedProcessOutput: String =
    SharedCollector.processOutput

  @TestOnly
  private[sbt] def clearSharedProcessOutput(): Unit =
    SharedCollector.clear()

  private[sbt] def collectProcessOutputFrom(processHandler: ProcessHandler, processTitle: String): Unit =
    sharedIfEnabled.foreach(_.collectProcessOutputFrom(processHandler, processTitle))

  private def isProcessOutputCollectionEnabled: Boolean =
    isUnitTestMode || java.lang.Boolean.getBoolean(PrintProcessOutputOnFailurePropertyName)

  private def isUnitTestMode: Boolean =
    ApplicationManager.getApplication.isUnitTestMode

  private final class ProcessOutputCollectingListener(
    processTitle: String,
    outputCollector: SbtProcessOutputDiagnosticsCollector,
  ) extends ProcessListener {
    override def onTextAvailable(event: ProcessEvent, outputType: Key[?]): Unit = {
      outputCollector.append(processTitle, event.getText)
    }
  }
}
