package org.jetbrains.sbt.runner

import com.intellij.debugger.DebugEnvironment
import com.intellij.debugger.engine.{DelayedRemoteConnection, DelayedRemoteConnectionImpl}
import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.{RemoteConnection, RunProfile, RunProfileState}
import com.intellij.execution.runToolbar.RunToolbarProcessData
import com.intellij.execution.runners.{ExecutionEnvironment, ExecutionUtil}
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.{executeOnPooledThread, executionContext, invokeAndWait, invokeLater}
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.runner.console.SbtShellWaitingForReadyHint
import org.jetbrains.sbt.runner.debugger.MyTrojanRemoteState
import org.jetbrains.sbt.settings.SbtSettings
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellCommunication}
import org.jetbrains.sbt.{SbtBundle, SbtUtil}

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

/**
 * Handles SBT task debug sessions only when they are delegated to the SBT shell.
 *
 * When [[SbtRunConfiguration.useSbtShell]] is disabled, this runner intentionally does not claim the configuration in [[canRun]].
 *
 * In that mode the standard platform debug runner executes the [[SbtCommandLineState]] as a
 * [[com.intellij.execution.configurations.JavaCommandLineState]],
 * using the Java command line assembled by [[SbtCommandLineState.createJavaParameters]].
 *
 * The fallback in [[shouldFallbackToNonDebugRunner]] is different: the configuration is still claimed by this runner,
 * but this runner executes it through SBT shell delegation instead of attaching the debugger.
 *
 * @see [[org.jetbrains.sbt.runner.SbtProgramRunner]]
 */
class SbtDebugProgramRunner extends GenericDebuggerRunner with SbtProgramRunnerBase {

  override def getRunnerId: String = "SbtDebugProgramRunner"

  override def canRun(executorId: String, profile: RunProfile): Boolean =
    isSbtRunConfigurationWithUseSbtShell(profile) && isDebugExecutorId(executorId)

  /**
   * @note This method is called on EDT (at least in 2026.2), even though there is not annotation contract
   */
  override def doExecute(state: RunProfileState, env: ExecutionEnvironment): RunContentDescriptor = {
    state match {
      case sbtState: SbtCommandLineState if shouldFallbackToNonDebugRunner(env, sbtState) =>
        // Don't show a dedicated run content, "sbt shell" tool window will be opened instead
        delegateExecutionToSbtShell(env, sbtState)
      case _ =>
        // Just do the standard thing - attach debugger to remote connection if possible and show it un Debug tool window
        super.doExecute(state, env)
    }
  }

  /**
   * @note This method is only called when `super.doExecute` is used TODO: check
   */
  @Nullable
  override def createContentDescriptor(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor =
    state match {
      case sbtState: SbtCommandLineState =>
        if (sbtState.configuration.useSbtShell)
          createContentDescriptorForSbtShellDelegation(environment, sbtState)
        else
          // default behavior - launch a separate process and attach the debugger to it
          super.createContentDescriptor(state, environment)
      case _ =>
        null
  }

  @Nullable
  private def createContentDescriptorForSbtShellDelegation(environment: ExecutionEnvironment, state: SbtCommandLineState): RunContentDescriptor = {
    val isShellDebuggingEnabled = isDebuggingInSbtShellInstanceOrSettingsEnabled(environment)
    if (isShellDebuggingEnabled) {
      createContentDescriptorForDebugConnection(environment, state)
    } else {
      throw new ExecutionException(SbtBundle.message("debugging.for.sbt.shell.is.disabled.in.sbt.settings"))
    }
  }

  @Nullable
  private def createContentDescriptorForDebugConnection(
    environment: ExecutionEnvironment,
    sbtState: SbtCommandLineState,
  ): RunContentDescriptor = {
    // Use a delayed connection because the sbt shell debug port is known only after the shell process is acquired off EDT.
    //noinspection ApiStatus,UnstableApiUsage
    val connection: DelayedRemoteConnectionImpl =
      new DelayedRemoteConnectionImpl(true, "localhost", "", false)

    val state = new MyTrojanRemoteState(environment, sbtState.configuration, connection)
    val attach: RunContentDescriptor = attachVirtualMachine(state, environment, connection, true)

    if (attach != null) {
      executeOnPooledThread {
        startSbtShellDebugCommandAsync(environment, sbtState, connection, state)
      }
    }

    attach
  }

  private class SbtShellWaitingMessagePrinter(state: MyTrojanRemoteState, project: Project) {
    // Debug-mode shell startup has two wait points: first while acquiring/attaching to the shell,
    // and later when the actual sbt command request is queued. Both can observe the same wait,
    // so keep the console hint behind a shared once-only guard.
    private val waitingHintPrinted = new AtomicBoolean(false)

    def printSbtShellWaitingHintOnce(): Unit =
      state.consoleView.foreach { consoleView =>
        // In the normal Debug path `attachVirtualMachine` has already created the execution console.
        // Keep this check defensive for platform/DAP/exceptional paths where the execution result has
        // no ConsoleView; in that case a later queue-time notification may still find a console and print.
        if (waitingHintPrinted.compareAndSet(false, true)) {
          SbtShellWaitingForReadyHint.print(consoleView)
        }
      }
  }

  /**
   * Runs the SBT-shell-backed debug command workflow after the Debug tool window content has been created.
   *
   * The caller schedules this method on a pooled thread because it may wait for the shell process and debug connection.
   * Once running, it:
   *  1. prepares the delayed debugger connection from the SBT shell process,
   *  2. prints the run-console waiting hint when the shell cannot accept the command immediately,
   *  3. submits the SBT command to the shell, and
   *  4. detaches the lightweight debug process when command execution finishes.
   */
  @RequiresBackgroundThread
  private def startSbtShellDebugCommandAsync(
    environment: ExecutionEnvironment,
    sbtState: SbtCommandLineState,
    connection: DelayedRemoteConnectionImpl,
    state: MyTrojanRemoteState
  ): Unit = {
    try {
      val waitingPrinter = new SbtShellWaitingMessagePrinter(state, environment.getProject)
      val sbtShellCommunication = SbtShellCommunication.forProject(environment.getProject)
      if (!sbtShellCommunication.isRunningAndIdle) {
        waitingPrinter.printSbtShellWaitingHintOnce()
      }

      ensureSbtShellStartedAndPrepareDelayedConnection(environment, state, connection)

      val commandFuture = submitCommandsToShell(
        environment,
        sbtState.processedCommands,
        state.processHandler,
        onQueuedWhileShellBusy = waitingPrinter.printSbtShellWaitingHintOnce,
      )

      commandFuture.onComplete { _ =>
        state.detach()
      }(using executionContext.appExecutionContext)
    } catch {
      case NonFatal(exception) =>
        state.detach()
        reportAsyncExecutionError(environment, exception)
    }
  }

  //noinspection ApiStatus,UnstableApiUsage
  private def ensureSbtShellStartedAndPrepareDelayedConnection(
    environment: ExecutionEnvironment,
    state: MyTrojanRemoteState,
    delayedConnection: DelayedRemoteConnection & RemoteConnection
  ): Unit = {
    val processManager = SbtProcessManager.forProject(environment.getProject)
    processManager.acquireShellProcessHandler(activateSbtShellToolWindowOnStartup = false)

    val shellDebugConnection = processManager.debugConnection.getOrElse {
      throw new ExecutionException(SbtBundle.message("debugging.for.sbt.shell.is.disabled.in.sbt.settings"))
    }
    copyRemoteConnection(shellDebugConnection, delayedConnection)
    runDelayedAttach(delayedConnection)
    // DelayedRemoteConnection returns after the connector obtains a VM, but before DebugProcessImpl finishes commitVM.
    // If a lightweight sbt command finishes in that gap, detaching can race the debugger attach lifecycle.
    state.awaitDebuggerAttached(DebugEnvironment.LOCAL_START_TIMEOUT.millis)
  }

  private def copyRemoteConnection(from: RemoteConnection, to: RemoteConnection): Unit = {
    to.setUseSockets(from.isUseSockets)
    to.setServerMode(from.isServerMode)
    to.setApplicationHostName(from.getApplicationHostName)
    to.setApplicationAddress(from.getApplicationAddress)
    to.setDebuggerHostName(from.getDebuggerHostName)
    to.setDebuggerAddress(from.getDebuggerAddress)
  }

  //noinspection ApiStatus,UnstableApiUsage
  private def runDelayedAttach(connection: DelayedRemoteConnection): Unit = {
    val attachRunnable = Option(connection.getAttachRunnable).getOrElse {
      throw new IllegalStateException("Debugger attach was not initialized")
    }
    invokeAndWait {
      attachRunnable.run()
    }
  }

  /**
   * Reports failures from the background SBT-shell debug startup workflow in the Debug tool window.
   *
   * These failures happen after `createContentDescriptor` has returned and the work has moved to a pooled thread.
   * Throwing from there would only fail that background task, so it would not be routed through the normal execution
   * error UI. Instead, convert the failure to an `ExecutionException` and dispatch it back to the platform error
   * handler on EDT.
   */
  private def reportAsyncExecutionError(environment: ExecutionEnvironment, exception: Throwable): Unit = {
    val executionException = exception match {
      case e: ExecutionException => e
      case other => new ExecutionException(other)
    }
    invokeLater {
      ExecutionUtil.handleExecutionError(
        environment.getProject,
        ToolWindowId.DEBUG,
        environment.getRunProfile.getName,
        executionException
      )
    }
  }

  /**
   * @return true - when sbt run configuration is executed as part of "Before launch" of another configuration,
   *         and sbt shell is used, and the debugging is not enabled for sbt shell
   *         false - otherwise
   *
   * @note If sbt shell is already running without a debug agent, we can't attach it without restarting sbt shell (which we don't do).<br>
   *       If sbt shell is not yet running, and debugging is enabled in the sbt settings, we can be sure that we will be able to attach the debug agent once sbt shell is acquired<br>
   *       If sbt shell is not yet running, and debugging is disabled, we don't override the setting here; it could be a confusing behavior.
   *
   * @see SCL-24434
   */
  private[runner] def shouldFallbackToNonDebugRunner(environment: ExecutionEnvironment, state: RunProfileState): Boolean = {
    val isInsideBeforeLaunch = isRunConfigurationExecutedAsPartOfBeforeLaunchStep(environment)
    if (!isInsideBeforeLaunch)
      return false

    state match {
      case sbtState: SbtCommandLineState if sbtState.configuration.useSbtShell =>
        val isDebuggingEnabled = isDebuggingInSbtShellInstanceOrSettingsEnabled(environment)
        !isDebuggingEnabled
      case _ =>
        false
    }
  }

  //See SCL-24434
  //noinspection UnstableApiUsage,ApiStatus
  private def isRunConfigurationExecutedAsPartOfBeforeLaunchStep(environment: ExecutionEnvironment): Boolean = {
    val config = environment.getRunnerAndConfigurationSettings
    val mainConfigurationId = environment.getUserData(RunToolbarProcessData.RW_MAIN_CONFIGURATION_ID)
    if (config == null || mainConfigurationId == null)
      false
    else
      config.getUniqueID != mainConfigurationId
  }

  private def isDebuggingInSbtShellInstanceOrSettingsEnabled(environment: ExecutionEnvironment): Boolean = {
    val project = environment.getProject
    val manager = SbtProcessManager.forProject(project)
    val isShellInitialized = manager.shellRunner.isDefined || manager.terminalConsole.isDefined
    val isDebuggingEnabled =
      if (isShellInitialized)
        manager.debugConnection.isDefined
      else
        isDebuggingInSbtSettingsEnabled(project)
    isDebuggingEnabled
  }

  private def isDebuggingInSbtSettingsEnabled(project: Project): Boolean = {
    val workingDirPath = SbtUtil.getWorkingDirPath(project)
    val settings = SbtSettings.getInstance(project).getLinkedProjectSettings(workingDirPath)
    Option(settings).getOrElse(SbtProjectSettings.default).enableDebugSbtShell
  }
}
