package org.jetbrains.sbt.runner

import com.intellij.debugger.engine.RemoteStateState
import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.execution.configurations.{RemoteConnection, RunProfileState}
import com.intellij.execution.runToolbar.RunToolbarProcessData
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.{ExecutionException, ExecutionResult, Executor}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.SbtExternalSystemManager
import org.jetbrains.sbt.shell.SbtProcessManager

/**
 * @see [[org.jetbrains.sbt.runner.SbtProgramRunner]]
 */
class SbtDebugProgramRunner extends GenericDebuggerRunner with SbtProgramRunnerBase {

  override def doExecute(state: RunProfileState, env: ExecutionEnvironment): RunContentDescriptor = {
    state match {
      case sbtState: SbtCommandLineState if shouldFallbackToNonDebugRunner(env, sbtState) =>
        // Don't create any content descriptor, "sbt shell" tool window will be opened instead
        delegateExecutionToSbtShell(env, sbtState)
        null
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
    val processManager = SbtProcessManager.forProject(environment.getProject)
    processManager.acquireShellProcessHandler()
    val shellDebugConnection = processManager.debugConnection
    shellDebugConnection match {
      case Some(connection) =>
        createContentDescriptorForDebugConnection(environment, state, connection)
      case None =>
        throw new ExecutionException(SbtBundle.message("debugging.for.sbt.shell.is.disabled.in.sbt.settings"))
    }
  }

  private def createContentDescriptorForDebugConnection(
    environment: ExecutionEnvironment,
    sbtState: SbtCommandLineState,
    connection: RemoteConnection
  ): RunContentDescriptor = {
    val state = new MyTrojanRemoteState(environment.getProject, connection)
    val attach = attachVirtualMachine(state, environment, connection, true)

    import org.jetbrains.plugins.scala.extensions.executionContext.appExecutionContext

    ApplicationManager.getApplication.executeOnPooledThread((() => {
      val commandFuture = submitCommands(environment, sbtState)
      commandFuture.onComplete { _ =>
        state.detach()
      }
    }): Runnable)

    attach
  }

  private class MyTrojanRemoteState(project: Project, connection: RemoteConnection) extends RemoteStateState(project, connection) {
    private var execResult: Option[ExecutionResult] = None

    override def execute(executor: Executor, runner: ProgramRunner[?]): ExecutionResult = {
      val er = super.execute(executor, runner)
      execResult = Option(er)
      er
    }

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
   * @note If sbt shell is already running without a debug agent, we can't attach it without restarting sbt shell (which we don't do).<br>
   *       If sbt shell is not yet running, and debugging is enabled in the sbt settings, we can be sure that we will be able to attach the debug agent once sbt shell is acquired<br>
   *       If sbt shell is not yet running, and debugging is disabled, we don't override the setting here; it could be a confusing behavior.
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
    val shellRunner = manager.shellRunner
    // See Scaladoc of `shouldFallbackToNonDebugRunner` for details
    val isDebuggingEnabled = if (shellRunner.isDefined)
      manager.debugConnection.isDefined
    else {
      val settings = SbtExternalSystemManager.executionSettingsFor(project)
      settings.shellDebugMode
    }
    isDebuggingEnabled
  }
}
