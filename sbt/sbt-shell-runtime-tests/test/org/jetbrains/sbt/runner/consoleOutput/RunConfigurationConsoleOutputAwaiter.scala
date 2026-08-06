package org.jetbrains.sbt.runner.consoleOutput

import org.jetbrains.sbt.runner.TestExecutionOptions
import org.jetbrains.sbt.runner.utils.{ExecutionDiagnostics, RunConfigurationExecutionObserver}
import org.junit.Assert.fail

import scala.concurrent.duration.{DurationInt, FiniteDuration}

private[runner] object RunConfigurationConsoleOutputAwaiter {
  private val DebuggerConnectedOutput = "Connected to the target VM"
  private val DebuggerDisconnectedOutput = "Disconnected from the target VM"
  private val FinalConsoleOutputTimeout: FiniteDuration = 10.seconds
  private val FinalConsoleOutputQuietPeriod: FiniteDuration = 500.millis
  private val FinalConsoleOutputPollingInterval: FiniteDuration = 50.millis

  def awaitFinalConsoleOutput(
    options: TestExecutionOptions,
    executionObserver: RunConfigurationExecutionObserver,
    expectedRunCommandOutput: String,
  ): String =
    ExecutionDiagnostics.withDiagnostics(Some(executionObserver)) {
      executionObserver.awaitSuccessfulTermination(timeout = FinalConsoleOutputTimeout)
      waitUntilFinalConsoleOutputIsStable(
        executionObserver,
        expectedTerminalConsoleFragments(options, expectedRunCommandOutput),
      )
    }

  private def expectedTerminalConsoleFragments(
    options: TestExecutionOptions,
    expectedRunCommandOutput: String,
  ): Seq[String] = {
    val debugFragments =
      if (options.expectsRunConfigurationDebugConnection) Seq(DebuggerConnectedOutput, DebuggerDisconnectedOutput)
      else Seq.empty
    expectedRunCommandOutput +: debugFragments
  }

  private def waitUntilFinalConsoleOutputIsStable(
    executionObserver: RunConfigurationExecutionObserver,
    requiredFragments: Seq[String],
  ): String = {
    val deadline = System.nanoTime() + FinalConsoleOutputTimeout.toNanos
    var lastOutput = executionObserver.consoleOutputSnapshot
    var stableSince = Option.empty[Long]

    while (System.nanoTime() <= deadline) {
      val now = System.nanoTime()
      val output = executionObserver.consoleOutputSnapshot
      val containsRequiredFragments = requiredFragments.forall(output.contains)
      val outputChanged = output != lastOutput

      if (containsRequiredFragments && !outputChanged) {
        stableSince match {
          case Some(stableSinceNanos) if now - stableSinceNanos >= FinalConsoleOutputQuietPeriod.toNanos =>
            return output
          case None =>
            stableSince = Some(now)
          case _ =>
        }
      } else {
        stableSince = Option.empty
      }

      lastOutput = output
      sleepBeforeNextConsoleSnapshot(deadline)
    }

    val finalOutput = executionObserver.consoleOutputSnapshot
    val missingFragments = requiredFragments.filterNot(finalOutput.contains)
    fail(
      s"""Timed out waiting for final run configuration console output to contain terminal fragments and stay unchanged for $FinalConsoleOutputQuietPeriod.
         |Missing terminal fragments:
         |${missingFragments.map(_.indent(2)).mkString}
         |Actual output:
         |${finalOutput.indent(2)}""".stripMargin,
    )
    finalOutput
  }

  private def sleepBeforeNextConsoleSnapshot(deadlineNanos: Long): Unit = {
    val remainingMillis = (deadlineNanos - System.nanoTime()) / 1000000L
    if (remainingMillis > 0) {
      Thread.sleep(math.min(FinalConsoleOutputPollingInterval.toMillis, remainingMillis))
    }
  }
}
