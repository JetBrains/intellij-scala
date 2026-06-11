package org.jetbrains.sbt.runner

import com.intellij.debugger.engine.{DelayedRemoteConnection, DelayedRemoteConnectionImpl, RemoteDebugProcessHandler, RemoteStateState}
import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.execution.configurations.{RemoteConnection, RunProfile, RunProfileState}
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.runToolbar.RunToolbarProcessData
import com.intellij.execution.runners.{ExecutionEnvironment, ExecutionUtil, ProgramRunner}
import com.intellij.execution.ui.{ConsoleView, RunContentDescriptor}
import com.intellij.execution.{DefaultExecutionResult, ExecutionException, ExecutionResult, Executor, JavaRunConfigurationExtensionManager}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.xdebugger.DapMode
import org.jetbrains.plugins.scala.extensions.{invokeAndWait, invokeLater}
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSettings
import org.jetbrains.sbt.shell.SbtProcessManager
import org.jetbrains.sbt.{SbtBundle, SbtUtil}

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

  private def createContentDescriptorForSbtShellDelegation(environment: ExecutionEnvironment, state: SbtCommandLineState): RunContentDescriptor = {
    val isShellDebuggingEnabled = isDebuggingInSbtShellInstanceOrSettingsEnabled(environment)
    if (isShellDebuggingEnabled) {
      createContentDescriptorForDebugConnection(environment, state)
    } else {
      throw new ExecutionException(SbtBundle.message("debugging.for.sbt.shell.is.disabled.in.sbt.settings"))
    }
  }

  private def createContentDescriptorForDebugConnection(
    environment: ExecutionEnvironment,
    sbtState: SbtCommandLineState,
  ): RunContentDescriptor = {
    // Use a delayed connection because the sbt shell debug port is known only after the shell process is acquired off EDT.
    //noinspection ApiStatus,UnstableApiUsage
    val connection: DelayedRemoteConnectionImpl =
      new DelayedRemoteConnectionImpl(true, "localhost", "", false)

    val state = new MyTrojanRemoteState(environment, sbtState.configuration, connection)
    val attach = attachVirtualMachine(state, environment, connection, true)

    if (attach != null) {
      import org.jetbrains.plugins.scala.extensions.executionContext.appExecutionContext

      ApplicationManager.getApplication.executeOnPooledThread((() => {
        try {
          ensureSbtShellStartedAndPrepareDelayedConnection(environment, connection)

          val commandFuture = submitCommands(environment, sbtState)
          commandFuture.onComplete { _ =>
            state.detach()
          }
        } catch {
          case NonFatal(exception) =>
            state.detach()
            reportAsyncExecutionError(environment, exception)
        }
      }): Runnable)
    }

    attach
  }

  //noinspection ApiStatus,UnstableApiUsage
  private def ensureSbtShellStartedAndPrepareDelayedConnection(
    environment: ExecutionEnvironment,
    delayedConnection: DelayedRemoteConnection & RemoteConnection
  ): Unit = {
    val processManager = SbtProcessManager.forProject(environment.getProject)
    processManager.acquireShellProcessHandler()

    val shellDebugConnection = processManager.debugConnection.getOrElse {
      throw new ExecutionException(SbtBundle.message("debugging.for.sbt.shell.is.disabled.in.sbt.settings"))
    }
    copyRemoteConnection(shellDebugConnection, delayedConnection)
    runDelayedAttach(delayedConnection)
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

  private class MyTrojanRemoteState(
    environment: ExecutionEnvironment,
    configuration: SbtRunConfiguration,
    connection: RemoteConnection,
  ) extends RemoteStateState(environment.getProject, connection) {
    private var execResult: Option[ExecutionResult] = None

    override def execute(executor: Executor, runner: ProgramRunner[?]): ExecutionResult = {
      val processHandler = new RemoteDebugProcessHandler(environment.getProject)
      val result: DefaultExecutionResult =
        if (DapMode.isDap)
          new DefaultExecutionResult(null, processHandler)
        else {
          val consoleView = new ConsoleViewImpl(environment.getProject, false)
          val decoratedConsoleView = decorateExecutionConsole(consoleView, executor)
          // We have to duplicate most of the logic of `RemoteStateState.execute` and can't just delegate to `super.execite`
          // Parent method attaches the plain console before decoration.
          // Console wrappers would miss attachToProcess, and LogCapture can fail during debugger session creation with
          //    > LogCapturingConsoleImpl.handlerWrapper accessed before attachToProcess() was called.
          //    > This usually means LogCapture.SessionData was created before the process handler was attached to the wrapping console
          //    > — the expected order is decorate -> attachToProcess -> DebuggerSession created. delegate=com.intellij.execution.impl.ConsoleViewImp
          decoratedConsoleView.attachToProcess(processHandler)
          new DefaultExecutionResult(decoratedConsoleView, processHandler)
        }
      execResult = Some(result)
      result
    }

    /**
     * Decorates the console for the remote debugger attach session.
     *
     * Unlike the regular non-shell SBT debug path, shell delegation does not go through
     * [[com.intellij.execution.configurations.JavaCommandLineState.createConsole]],
     * so Java run configuration extensions do not get a chance to wrap the console there.
     *
     * Applying the decoration here keeps this custom remote-debug path aligned with normal Java run/debug execution
     * and lets debugger console integrations see the expected console shape.
     */
    private def decorateExecutionConsole(consoleView: ConsoleView, executor: Executor): ConsoleView =
      // Reuse the SBT run configuration and the original runner settings,
      // so enabled Java run configuration extensions make the same decision here as they do in the regular JavaCommandLineState path.
      JavaRunConfigurationExtensionManager.getInstance.decorateExecutionConsole(
        configuration,
        environment.getRunnerSettings,
        consoleView,
        executor
      )

    def detach(): Unit = {
      execResult.foreach {
        result =>
          Option(result.getProcessHandler).foreach(_.detachProcess())
      }
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
