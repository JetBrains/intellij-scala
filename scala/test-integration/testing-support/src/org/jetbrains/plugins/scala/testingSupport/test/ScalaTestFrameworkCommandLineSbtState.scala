package org.jetbrains.plugins.scala.testingSupport.test

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.{ExecutionResult, Executor}
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.statistics.ScalaSbtUsagesCollector
import org.jetbrains.plugins.scala.testingSupport.test.sbt.{ReportingSbtTestEventHandler, SbtProcessHandlerWrapper, SbtShellTestsRunner, SbtTestRunningSupport}
import org.jetbrains.plugins.scala.testingSupport.test.utils.RawProcessOutputDebugLogger
import org.jetbrains.sbt.shell.SbtProcessManager

@ApiStatus.Internal
class ScalaTestFrameworkCommandLineSbtState(
  override val configuration: AbstractTestRunConfiguration,
  env: ExecutionEnvironment,
  override val failedTests: Option[Seq[(String, String)]],
  sbtSupport: SbtTestRunningSupport
) extends CommandLineState(env)
    with ScalaTestFrameworkCommandLineStateLike {

  override def startProcess(): ProcessHandler = {
    //use a process running sbt
    val sbtProcessManager = SbtProcessManager.forProject(project)
    //make sure the process is initialized
    val shellRunner = sbtProcessManager.acquireShellRunner()
    SbtProcessHandlerWrapper(shellRunner.createProcessHandler)
  }

  override def execute(executor: Executor, runner: ProgramRunner[_]): ExecutionResult = {
    val useUiWithSbt = testConfigurationData.useUiWithSbt

    val processHandler = startProcess()

    attachExtensionsToProcess(configuration, processHandler)

    RawProcessOutputDebugLogger.maybeAddListenerTo(processHandler)

    val consoleView = if (useUiWithSbt) {
      val consoleProperties = configuration.createTestConsoleProperties(executor)
      consoleProperties.setIdBasedTestTree(true)
      SMTestRunnerConnectionUtil.createConsole("Scala", consoleProperties)
    } else {
      new ConsoleViewImpl(project, true)
    }
    consoleView.attachToProcess(processHandler)

    val executionResult = createExecutionResult(consoleView, processHandler)

    ScalaSbtUsagesCollector.logShellTestRunCommand(project)
    val suitesToTestsMap = buildSuitesToTestsMap
    val future = SbtShellTestsRunner.runTestsInSbtShell(
      sbtSupport,
      module,
      suitesToTestsMap,
      new ReportingSbtTestEventHandler((message, key) => {
        processHandler.notifyTextAvailable(message, key)
      }),
      useUiWithSbt
    )
    future.onComplete(_ => processHandler.destroyProcess())(sbtSupport.executionContext)

    executionResult
  }
}


