package org.jetbrains.sbt.runner.debugger

import com.intellij.debugger.DebuggerManager
import com.intellij.debugger.engine.{DebugProcessImpl, RemoteStateState}
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.{DefaultExecutionResult, ExecutionException, ExecutionResult, Executor, JavaRunConfigurationExtensionManager}
import com.intellij.xdebugger.DapMode
import org.jetbrains.plugins.scala.extensions.OptionExt
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.runner.SbtRunConfiguration

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/**
 * Remote debug state for SBT shell delegation.
 *
 * It has two responsibilities:
 *  - keep the created execution result so [[org.jetbrains.sbt.runner.SbtDebugProgramRunner]] can detach the remote debug process
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
  import AttachWaitResult.{AlreadyAttached, AttachedAfterWaiting}

  private var execResult: Option[ExecutionResult] = None

  override def execute(executor: Executor, runner: ProgramRunner[?]): ExecutionResult = {
    val result = executeImpl(executor)
    execResult = Some(result)
    result
  }

  private def executeImpl(executor: Executor): DefaultExecutionResult = {
    val processHandler = new SbtRemoteDebugProcessHandler(environment.getProject)
    if (DapMode.isDap)
      new DefaultExecutionResult(null, processHandler)
    else {
      val consoleView = new ConsoleViewImpl(environment.getProject, false)

      // ATTENTION: the most important difference with `super.execute`.
      // Platform `RemoteStateState.execute` attaches a plain `ConsoleViewImpl` before it returns.
      // SBT shell debug needs Java console decorators installed before `attachToProcess`,
      // otherwise wrappers such as LogCapture can observe debugger session creation before their own `attachToProcess` call.
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
    val processHandler = execResult.flatMap(r => Option(r.getProcessHandler))
    processHandler.foreach { handler =>
      handler.detachProcess()
      handler match {
        case handler: SbtRemoteDebugProcessHandler =>
          // SBT shell debug uses this remote debug process handler as the run-configuration process.
          // The `detachProcess()` call asks the debugger to detach asynchronously, and normally the platform later calls `notifyProcessDetached()`.
          // However, in the shell flow the sbt command can finish while the remote attach/detach lifecycle is still settling,
          // so keep a bounded fallback notification to unblock execution listeners and before-launch tasks if the platform callback never arrives.
          handler.notifyDetachedIfNeededAfter(10.seconds)
        case _ =>
      }
    }
  }

  /**
   * Waits until the Java debugger has fully attached to the delayed SBT shell remote connection.
   *
   * Delayed remote attach has two observable phases: the connector can obtain a VM first, and the debugger commits that VM
   * to the debug process shortly after. Detaching between these phases can leave the remote process handler without a
   * reliable termination notification.
   */
  @throws[ExecutionException]
  def awaitDebuggerAttached(timeout: FiniteDuration): Unit = {
    val handler = processHandler.getOrElse {
      throw new ExecutionException(SbtBundle.message("sbt.shell.debug.process.handler.is.not.initialized"))
    }
    val debugProcess0 = Option(DebuggerManager.getInstance(environment.getProject).getDebugProcess(handler))
    val debugProcess = debugProcess0.filterByType[DebugProcessImpl].getOrElse {
      throw new ExecutionException(SbtBundle.message("sbt.shell.debug.process.is.not.initialized"))
    }

    DebuggerAttachListener.awaitAttachSignalIfNeeded(debugProcess, timeout) match {
      case AlreadyAttached =>
      case AttachedAfterWaiting =>
        DebuggerCommandListener.awaitScheduledCommand(debugProcess, timeout)
    }
  }

  def processHandler: Option[ProcessHandler] =
    execResult.flatMap(result => Option(result.getProcessHandler))

  def consoleView: Option[ConsoleView] = {
    val executionConsole = execResult.flatMap(result => Option(result.getExecutionConsole))
    executionConsole.filterByType[ConsoleView]
  }
}
