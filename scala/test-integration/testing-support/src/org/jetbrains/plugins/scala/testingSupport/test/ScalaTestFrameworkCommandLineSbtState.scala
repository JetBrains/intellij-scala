package org.jetbrains.plugins.scala.testingSupport.test

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.{ExecutionResult, Executor}
import org.jetbrains.annotations.{ApiStatus, Nullable}
import org.jetbrains.plugins.scala.build.BuildMessages
import org.jetbrains.plugins.scala.extensions.executeOnPooledThread
import org.jetbrains.plugins.scala.statistics.SbtShellCommandsUsagesCollector
import org.jetbrains.plugins.scala.testingSupport.test.sbt.{ReportingSbtTestEventHandler, SbtShellTestsRunner, SbtTestRunningSupport}
import org.jetbrains.plugins.scala.testingSupport.test.utils.RawProcessOutputDebugLogger
import org.jetbrains.sbt.runner.RunContentConsoleOutputProcessor
import org.jetbrains.sbt.shell.communication.SbtShellCommandEventProcessor

import java.io.OutputStream

@ApiStatus.Internal
class ScalaTestFrameworkCommandLineSbtState(
  override val configuration: AbstractTestRunConfiguration,
  env: ExecutionEnvironment,
  override val failedTests: Option[Seq[(String, String)]],
  sbtSupport: SbtTestRunningSupport
) extends CommandLineState(env)
    with ScalaTestFrameworkCommandLineStateLike {

  override def startProcess(): ProcessHandler =
    new DummyProcessHandler // It's not used anyway

  override def execute(executor: Executor, runner: ProgramRunner[_]): ExecutionResult = {
    val useUiWithSbt = testConfigurationData.useUiWithSbt

    val processHandler = new DummyProcessHandler

    attachExtensionsToProcess(configuration, processHandler)

    RawProcessOutputDebugLogger.maybeAddListenerTo(processHandler)

    val consoleView =
      if (useUiWithSbt) {
        val consoleProperties = configuration.createTestConsoleProperties(executor)
        consoleProperties.setIdBasedTestTree(true)
        SMTestRunnerConnectionUtil.createConsole("Scala", consoleProperties)
      } else {
        new ConsoleViewImpl(project, true) {
          override def print(text: String, contentType: ConsoleViewContentType, info: HyperlinkInfo): Unit =
            super.print(BuildMessages.stripAnsiCodes(text), contentType, info)
        }
      }
    consoleView.attachToProcess(processHandler)

    // Sbt shell does not use a decorated console view, passing the same instance twice means that the execution result
    // and restart actions are created for the same console view instance.
    val executionResult = createExecutionResult(consoleView, consoleView, processHandler)

    SbtShellCommandsUsagesCollector.logShellTestRunCommand(project)
    val suitesToTestsMap = buildSuitesToTestsMap

    val shellEventProcessor = createShellEventProcessor(processHandler)

    executeOnPooledThread {
      val future = SbtShellTestsRunner.runTestsInSbtShell(
        sbtSupport,
        module,
        suitesToTestsMap,
        shellEventProcessor,
        useUiWithSbt
      )
      future.onComplete { result =>
        // Generating the exit code based on whether the future completed successfully or not
        // does not indicate whether the tests passed. I don't see any practical difference
        // between using an exit code 0 or 1 here. It might be irrelevant for both sbt test
        // runs with and without the UI. In the previous implementation, the exit code was always hardcoded to 0.
        val exitCode = if (result.isSuccess) 0 else 1
        processHandler.terminate(exitCode)
      }(sbtSupport.executionContext)
    }

    executionResult
  }

  /**
   * Creates a composite shell event processor that handles two concerns:
   *  - '''Console output''': [[RunContentConsoleOutputProcessor]] forwards every `ShellEvent.Output` line
   *    to the `processHandler`, so raw sbt output appears in the Run tool window console.
   *  - '''Test tree events''': [[ReportingSbtTestEventHandler]] parses raw sbt test output lines and generates `##teamcity[...]` messages.
   */
  private def createShellEventProcessor(
    processHandler: ProcessHandler
  ): SbtShellCommandEventProcessor[Unit] = {
    val sbtEventsHandler = new ReportingSbtTestEventHandler((message, key) =>
      processHandler.notifyTextAvailable(message, key)
    )
    val testEvents = new SbtShellCommandEventProcessor.ShellEventListener(sbtEventsHandler.processEvent)
    val consoleOutput = new RunContentConsoleOutputProcessor(processHandler)
    testEvents.tap(consoleOutput)
  }

  /**
   * The current limitation of this handler is that it does not support cancelling a running
   * `test` task (SCL-25617). Once this is implemented, a single dummy handler could be shared
   * between ScalaTest Run Configuration and sbt task Run configuration.
   *
   * @see [[org.jetbrains.sbt.runner.SbtProgramRunnerBase.DummyProcessHandler]]
   */
  private class DummyProcessHandler extends ProcessHandler {
    def terminate(exitCode: Int): Unit =
      notifyProcessTerminated(exitCode)

    override def destroyProcessImpl(): Unit =
      notifyProcessTerminated(1)

    override def detachProcessImpl(): Unit =
      destroyProcessImpl()

    override def detachIsDefault(): Boolean = false

    @Nullable override def getProcessInput: OutputStream = null
  }
}
