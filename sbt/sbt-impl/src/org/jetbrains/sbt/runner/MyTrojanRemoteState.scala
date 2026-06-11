package org.jetbrains.sbt.runner

import com.intellij.debugger.engine.{RemoteDebugProcessHandler, RemoteStateState}
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.{DefaultExecutionResult, ExecutionResult, Executor, JavaRunConfigurationExtensionManager}
import com.intellij.xdebugger.DapMode

/**
 * Remote debug state for SBT shell delegation.
 *
 * It has two responsibilities:
 *  - keep the created execution result so [[SbtDebugProgramRunner]] can detach the remote debug process
 *    after the asynchronous SBT shell command finishes or fails;
 *  - reproduce [[RemoteStateState.execute]] with one intentional difference:
 *    the console is decorated through Java run configuration extensions before it is attached to the remote debug process.
 *
 * The second responsibility is needed because SBT shell debug delegation attaches to the already-running SBT shell JVM and does not go through
 * [[com.intellij.execution.configurations.JavaCommandLineState.createConsole]].
 * If we delegated to [[RemoteStateState.execute]], it would attach a plain [[ConsoleViewImpl]] first and return an
 * execution result too late for wrappers such as LogCapture.<br>
 * Those wrappers can then observe debugger session creation before their own `attachToProcess` call and fail with
 * `LogCapturingConsoleImpl.handlerWrapper accessed before attachToProcess() was called`.
 */
private[runner] final class MyTrojanRemoteState(
  environment: ExecutionEnvironment,
  configuration: SbtRunConfiguration,
  connection: RemoteConnection,
) extends RemoteStateState(environment.getProject, connection) {
  private var execResult: Option[ExecutionResult] = None

  override def execute(executor: Executor, runner: ProgramRunner[?]): ExecutionResult = {
    val result = executeImpl(executor)
    execResult = Some(result)
    result
  }

  private def executeImpl(executor: Executor): DefaultExecutionResult = {
    val processHandler = new RemoteDebugProcessHandler(environment.getProject)
    if (DapMode.isDap)
      new DefaultExecutionResult(null, processHandler)
    else {
      val consoleView = new ConsoleViewImpl(environment.getProject, false)

      // ATTENTION: the most important difference with `super.execute`
      val decoratedConsoleView = decorateExecutionConsole(consoleView, executor)
      decoratedConsoleView.attachToProcess(processHandler)

      new DefaultExecutionResult(decoratedConsoleView, processHandler)
    }
  }

  private def decorateExecutionConsole(consoleView: ConsoleView, executor: Executor): ConsoleView =
    JavaRunConfigurationExtensionManager.getInstance.decorateExecutionConsole(
      configuration,
      environment.getRunnerSettings,
      consoleView,
      executor
    )

  def detach(): Unit = {
    execResult.foreach { result =>
      Option(result.getProcessHandler).foreach(_.detachProcess())
    }
  }
}
