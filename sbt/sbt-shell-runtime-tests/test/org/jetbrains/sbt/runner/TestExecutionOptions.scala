package org.jetbrains.sbt.runner

import com.intellij.execution.Executor
import com.intellij.execution.executors.{DefaultDebugExecutor, DefaultRunExecutor}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.sbt.runner.TestExecutionOptions.{ExecutionMode, SbtProcessMode}

/**
 * @param enableDebuggingInShell this is stored separately from [[sbtProcessMode]] to be able to calculate the default based on the execution mode
 */
private final case class TestExecutionOptions(
  executionMode: ExecutionMode,
  sbtProcessMode: SbtProcessMode,
  enableDebuggingInShell: Boolean,
) {
  def useSbtShellInRunConfig: Boolean = sbtProcessMode != SbtProcessMode.NoShell

  def useNewSbtShell: Boolean = sbtProcessMode == SbtProcessMode.Shell(isNewShell = true)
}

private object TestExecutionOptions {

  def apply(
    executionMode: ExecutionMode,
    sbtProcessMode: SbtProcessMode
  ): TestExecutionOptions = {
    val enableDebuggingInShell = executionMode == ExecutionMode.Debug && sbtProcessMode.is[SbtProcessMode.Shell]
    TestExecutionOptions(executionMode, sbtProcessMode, enableDebuggingInShell = enableDebuggingInShell)
  }

  enum SbtProcessMode {
    case NoShell extends SbtProcessMode
    case Shell(isNewShell: Boolean) extends SbtProcessMode
  }
  object SbtProcessMode {
    val OldShell: SbtProcessMode = Shell(isNewShell = false)
    val NewShell: SbtProcessMode = Shell(isNewShell = true)
  }

  enum ExecutionMode(val displayName: String) {
    case Run extends ExecutionMode("Run")
    case Debug extends ExecutionMode("Debug")

    def executor: Executor = this match {
      case Run => DefaultRunExecutor.getRunExecutorInstance
      case Debug => DefaultDebugExecutor.getDebugExecutorInstance
    }
  }
}
