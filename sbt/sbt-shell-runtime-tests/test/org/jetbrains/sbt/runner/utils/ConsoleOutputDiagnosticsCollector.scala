package org.jetbrains.sbt.runner.utils

import com.intellij.execution.console.ConsoleViewWrapperBase
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.{ConsoleViewContentType, ConsoleViewWithDelegate, ExecutionConsole, ObservableConsoleView, RunContentDescriptor}
import com.intellij.openapi.Disposable
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.sbt.process.SbtProcessOutputDiagnosticsCollector
import org.jetbrains.sbt.runner.utils.DiagnosticOutputFormatter.{printSection, sections}

import java.io.PrintStream
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

private[runner] final class ConsoleOutputDiagnosticsCollector(
  configurationName: String,
  parentDisposable: Disposable,
  captureConsoleOutput: Boolean,
) {
  private val runContentDescriptor = new AtomicReference[RunContentDescriptor]()
  private val consoleListenerInstalled = new AtomicBoolean(false)
  private val consoleOutput = new StringBuffer
  private val consoleCaptureDiagnostics = new StringBuffer

  def recordRunContentDescriptorIfMatches(descriptor: RunContentDescriptor): Unit = {
    if (descriptor.getRunConfigurationName == configurationName) {
      recordRunContentDescriptor(descriptor)
    }
  }

  def recordRunContentDescriptor(descriptor: RunContentDescriptor): Unit = {
    initializeConsoleUi(descriptor)
    if (!captureConsoleOutput) {
      return
    }
    appendConsoleCaptureDiagnostics(describeRunContentDescriptor("recordRunContentDescriptor", descriptor))
    if (runContentDescriptor.compareAndSet(null, descriptor)) {
      appendConsoleOutput(consoleOutputFromDescriptor(descriptor))
      recordExecutionConsole(descriptor.getExecutionConsole)
    } else {
      appendConsoleCaptureDiagnostics("RunContentDescriptor was already recorded")
    }
  }

  def recordExecutionConsole(console: ExecutionConsole): Unit = {
    if (!captureConsoleOutput)
      return

    appendConsoleCaptureDiagnostics(describeExecutionConsole("recordExecutionConsole", console))
    appendConsoleOutput(consoleOutputFromConsole(console))
    if (consoleListenerInstalled.compareAndSet(false, true)) {
      observableConsoleFrom(console) match {
        case Some(observableConsole) =>
          appendConsoleCaptureDiagnostics(s"Installing ObservableConsoleView listener on ${observableConsole.getClass.getName}")
          observableConsole.addChangeListener(
            new ObservableConsoleView.ChangeListener {
              override def textAdded(text: String, `type`: ConsoleViewContentType): Unit =
                appendConsoleOutput(text)
            },
            parentDisposable,
          )
        case None =>
          appendConsoleCaptureDiagnostics("ObservableConsoleView was not found")
      }
    }
  }

  def consoleOutputSnapshot: String = {
    val liveConsoleOutput = Option(runContentDescriptor.get())
      .map(consoleOutputFromDescriptor)
      .getOrElse("")
    val observedConsoleOutput = bufferText(consoleOutput)

    if (liveConsoleOutput.isEmpty) observedConsoleOutput
    else if (observedConsoleOutput.isEmpty) liveConsoleOutput
    else if (liveConsoleOutput.contains(observedConsoleOutput)) liveConsoleOutput
    else if (observedConsoleOutput.contains(liveConsoleOutput)) observedConsoleOutput
    else liveConsoleOutput + observedConsoleOutput
  }

  def consoleCaptureDiagnosticsSnapshot: String =
    bufferText(consoleCaptureDiagnostics)

  def diagnosticsSnapshot(runConfigurationProcessOutput: String): String =
    sections(
      "Run configuration console output" -> consoleOutputSnapshot,
      "Run configuration console capture diagnostics" -> consoleCaptureDiagnosticsSnapshot,
      "Run configuration process output" -> runConfigurationProcessOutput,
      "SBT process output" -> SbtProcessOutputDiagnosticsCollector.sharedProcessOutput,
    )

  def printDiagnostics(runConfigurationProcessOutput: String, out: PrintStream): Unit = {
    printSection("Run configuration console output", consoleOutputSnapshot, out)
    printSection("Run configuration console capture diagnostics", consoleCaptureDiagnosticsSnapshot, out)
    printSection("Run configuration process output", runConfigurationProcessOutput, out)
    printSection("SBT process output", SbtProcessOutputDiagnosticsCollector.sharedProcessOutput, out)
  }

  private def appendConsoleOutput(text: String): Unit =
    if (text.nonEmpty) {
      consoleOutput.synchronized {
        consoleOutput.append(text)
      }
    }

  private def appendConsoleCaptureDiagnostics(text: String): Unit =
    consoleCaptureDiagnostics.synchronized {
      consoleCaptureDiagnostics.append(text).append('\n')
    }

  private def initializeConsoleUi(descriptor: RunContentDescriptor): Unit =
    invokeAndWait {
      /*
       * Unit-test mode still publishes beforeContentShown, but RunContentManagerImpl may skip the real tab showing
       * path. Some execution-console wrappers, notably LogCapture, lazily create UI in getComponent() and expect that
       * product UI has done it before disposal. Initialize the matched descriptor's console UI here even when console
       * capture is disabled, so teardown follows the same lifecycle as a shown run tab.
       */
      Option(descriptor.getExecutionConsole).foreach(_.getComponent)
      descriptor.getComponent
    }

  private def consoleOutputFromDescriptor(descriptor: RunContentDescriptor): String =
    consoleViewFrom(descriptor)
      .map(consoleText)
      .getOrElse("")

  private def consoleOutputFromConsole(console: ExecutionConsole): String =
    consoleViewImplFrom(console)
      .map(consoleText)
      .getOrElse("")

  private def describeExecutionConsole(stage: String, console: ExecutionConsole): String = {
    val consoleView = consoleViewImplFrom(console)
    val observableConsole = observableConsoleFrom(console)
    s"""$stage:
       |  executionConsole: ${Option(console).map(_.getClass.getName).getOrElse("<null>")}
       |  ConsoleViewImpl: ${consoleView.map(_.getClass.getName).getOrElse("<not found>")}
       |  ObservableConsoleView: ${observableConsole.map(_.getClass.getName).getOrElse("<not found>")}""".stripMargin
  }

  private def describeRunContentDescriptor(stage: String, descriptor: RunContentDescriptor): String = {
    val executionConsole = descriptor.getExecutionConsole
    val component = descriptor.getComponent
    val consoleView = consoleViewFrom(descriptor)
    val observableConsole = observableConsoleFrom(executionConsole)
    s"""$stage:
       |  descriptor: ${descriptor.getClass.getName}
       |  displayName: ${descriptor.getDisplayName}
       |  executionConsole: ${Option(executionConsole).map(_.getClass.getName).getOrElse("<null>")}
       |  processHandler: ${Option(descriptor.getProcessHandler).map(_.getClass.getName).getOrElse("<null>")}
       |  component: ${Option(component).map(_.getClass.getName).getOrElse("<null>")}
       |  ConsoleViewImpl: ${consoleView.map(_.getClass.getName).getOrElse("<not found>")}
       |  ObservableConsoleView: ${observableConsole.map(_.getClass.getName).getOrElse("<not found>")}""".stripMargin
  }

  private def consoleViewFrom(descriptor: RunContentDescriptor): Option[ConsoleViewImpl] =
    consoleViewImplFrom(descriptor.getExecutionConsole).orElse {
      Option(descriptor.getComponent)
        .flatMap(component => Option(UIUtil.findComponentOfType(component, classOf[ConsoleViewImpl])))
    }

  private def consoleViewImplFrom(console: ExecutionConsole): Option[ConsoleViewImpl] =
    console match {
      case console: ConsoleViewImpl => Some(console)
      case wrapper: ConsoleViewWrapperBase => consoleViewImplFrom(wrapper.getDelegate)
      case wrapper: ConsoleViewWithDelegate => consoleViewImplFrom(wrapper.getDelegate)
      case _ =>
        None
    }

  private def observableConsoleFrom(console: ExecutionConsole): Option[ObservableConsoleView] =
    console match {
      case observableConsole: ObservableConsoleView => Some(observableConsole)
      case wrapper: ConsoleViewWrapperBase => observableConsoleFrom(wrapper.getDelegate)
      case wrapper: ConsoleViewWithDelegate => observableConsoleFrom(wrapper.getDelegate)
      case _ => None
    }

  private def consoleText(console: ConsoleViewImpl): String = {
    var text = ""
    invokeAndWait {
      console.flushDeferredText()
      text = Option(console.getEditor).map(_.getDocument.getText).getOrElse("")
    }
    text
  }

  def consoleInlayOffsetsAfterTextSnapshot(text: String): Seq[Int] = invokeAndWait {
    ConsoleInlayHintUtils.inlayOffsetsAfterTextInConsole(consoleViewFromRecordedDescriptor, text)
  }

  private def consoleViewFromRecordedDescriptor: Option[ConsoleViewImpl] =
    Option(runContentDescriptor.get()).flatMap(consoleViewFrom)

  private def bufferText(output: StringBuffer): String =
    output.synchronized {
      output.toString
    }
}
