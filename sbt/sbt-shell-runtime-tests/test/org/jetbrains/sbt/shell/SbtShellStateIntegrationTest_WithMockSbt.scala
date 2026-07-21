package org.jetbrains.sbt.shell

import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.sbt.process.mock.{MockSbtProcessCommands, MockSbtProcessForTestsSetup}
import org.jetbrains.sbt.shell.SbtShellDiagnosticEvent.*
import org.jetbrains.sbt.shell.communication.SbtShellLifecycle.ShellState
import org.jetbrains.sbt.shell.communication.{SbtShellCommandRequest, SbtShellCommandRequestId}
import org.junit.Assert.{assertTrue, fail}
import org.junit.experimental.categories.Category

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.Failure

/**
 * Duplicates [[SbtShellStateIntegrationTest]] with the mock sbt process enabled.
 *
 * The mock is a lightweight sbt-process substitute for straightforward shell lifecycle, command queue, output, restart,
 * shutdown, and shell-based project import scenarios. It intentionally does not emulate terminal internals such as raw
 * input mode, OS-level echo settings, or single-character interactive prompts; those behaviors should be covered with
 * real sbt.
 */
@Category(Array(classOf[SlowTests2]))
class SbtShellStateIntegrationTest_WithMockSbt extends SbtShellStateIntegrationTest {

  override protected def setupBeforeProjectImport(): Unit = {
    super.setupBeforeProjectImport()
    MockSbtProcessForTestsSetup.enableMockSbtProcess(getMyProject, getTestRootDisposable)
  }

  // Tests the "happy path" of the soft restart scenario. While the soft restart is in
  // progress, a new command is added. After the shell restarts, this command are executed.
  def testSoftRestart_AfterRestartCommandsAreExecuted(): Unit = {
    val markerFile = getTestProjectPath.resolve(s"${getTestName(false)}.marker")

    try {
      val checker = StateSequenceChecker.start(
        ShellState.Idle,
        ShellState.Queued,
        ShellState.SoftRestarting,
        ShellState.SoftRestarting,
        ShellState.ShuttingDown,
        ShellState.Off,
        ShellState.Starting,
        ShellState.Queued,
        ShellState.Queued,
        ShellState.Idle
      )

      // Step 1: Block the shell with mockWaitForFile
      val blockingCommand = MockSbtProcessCommands.waitForFileCommand(markerFile)
      val blockingFuture = shellCommunication.runAndCollectOutput(blockingCommand)

      // Step 2: Start soft restart (it blocks waiting for queue to be empty)
      val restartRequestId = SbtShellCommandRequestId("soft-restart-command")
      val restartFutureRef = new AtomicReference[Future[StringBuilder]](null)

      import scala.concurrent.ExecutionContext.Implicits.global
      Future {
        val future = shellCommunication.runAfterSoftRestart(SbtShellCommandRequest.collectOutput("task", restartRequestId))
        restartFutureRef.set(future)
      }

      // Wait until soft restart is in progress
      AwaitTestUtils.waitForConditionOrFail(20.seconds, "Shell did not enter soft restart process") {
        () => shellCommunication.currentState.isSoftRestarting
      }

      // Step 3: Submit additional command — routed to afterRestartCommands buffer
      val additionalRequestId = SbtShellCommandRequestId("after-restart")
      val additionalRequestFuture = shellCommunication.run(SbtShellCommandRequest.collectOutput("task", additionalRequestId))

      // Step 4: Release the blocking command by creating the marker file
      Files.writeString(markerFile, "release")

      // Step 5: Wait for the full state sequence to complete
      checker.awaitSuccessful(20.seconds)

      // Step 6: Wait for all futures to complete
      val futures = Future.sequence(List(blockingFuture, restartFutureRef.get(), additionalRequestFuture))
      Await.result(futures, commandWaitTimeout)

      // Step 7: Verify all commands were executed
      val events = shellCommunication.diagnosticEventsSnapshot
      val snapshot = shellCommunication.diagnosticsSnapshot

      assertDiagnosticEventExists[EnqueueAfterRestartCommands](events, restartRequestId, snapshot)
      assertDiagnosticEventExists[ProcessCommandStart](events, restartRequestId, snapshot)

      assertDiagnosticEventExists[EnqueueAfterRestartCommands](events, additionalRequestId, snapshot)
      assertDiagnosticEventExists[ProcessCommandStart](events, additionalRequestId, snapshot)
    } finally {
      // release waiting for mock command
      Files.writeString(markerFile, "release")
    }
  }

  /**
   * During a hard kill, the shell transitions to [[ShellState.ShuttingDown]].
   * The gate is still open, so the command goes to `afterRestartCommands`, gets flushed to `commands`, and executes in the new shell.
   */
  def testCommandSubmittedDuringShuttingDown_isAccepted_HardKill(): Unit = {
    val requestId = SbtShellCommandRequestId("task")
    val requestFuture = new AtomicReference[Future[StringBuilder]](null)

    // Add a listener that sends a new command when the shell enters the ShuttingDown state
    val submitListener: ShellState => Unit = { state =>
      if state == ShellState.ShuttingDown then
        val future = shellCommunication.run(SbtShellCommandRequest.collectOutput("task", requestId))
        requestFuture.set(future)
    }
    shellCommunication.addTestStateListener(submitListener)

    try {
      val checker = StateSequenceChecker.start(
        ShellState.Idle,
        ShellState.ShuttingDown,
        ShellState.Off,
        ShellState.Starting,
        ShellState.Queued,
        ShellState.Idle
      )

      SbtProcessManager.forProject(project).destroyProcess()

      checker.awaitSuccessful(20.seconds)

      Await.result(requestFuture.get(), commandWaitTimeout)

      val events = shellCommunication.diagnosticEventsSnapshot
      val snapshot = shellCommunication.diagnosticsSnapshot

      assertDiagnosticEventExists[EnqueueAfterRestartCommands](events, requestId, snapshot)
      assertDiagnosticEventExists[ProcessCommandStart](events, requestId, snapshot)
    } finally {
      shellCommunication.removeTestStateListener(submitListener)
    }
  }

  /**
   * During a soft restart's shutdown, the shell transitions to [[ShellState.ShuttingDown]].
   * The gate is still open, so the command goes to `afterRestartCommands`, gets flushed to `commands`, and executes in the new shell.
   */
  def testCommandSubmittedDuringShuttingDown_isAccepted_SoftRestart(): Unit = {
    val markerFile = getTestProjectPath.resolve(s"${getTestName(false)}.marker")

    val restartRequestId = SbtShellCommandRequestId("soft-restart-command")
    val taskId = SbtShellCommandRequestId("task")

    val restartFuture = new AtomicReference[Future[StringBuilder]](null)
    val taskFuture = new AtomicReference[Future[StringBuilder]](null)

    // Add a listener that sends a new command when the shell enters the ShuttingDown state
    val submitListener: ShellState => Unit = { state =>
      if state == ShellState.ShuttingDown then
        val future = shellCommunication.run(SbtShellCommandRequest.collectOutput("task", taskId))
        taskFuture.set(future)
    }
    shellCommunication.addTestStateListener(submitListener)

    try {
      val checker = StateSequenceChecker.start(
        ShellState.Idle,
        ShellState.Queued,
        ShellState.SoftRestarting,
        ShellState.SoftRestarting,
        ShellState.ShuttingDown,
        ShellState.Off,
        ShellState.Starting,
        ShellState.Queued,
        ShellState.Queued,
        ShellState.Idle
      )

      // Step 1: Block the shell with mockWaitForFile
      val blockingCommand = MockSbtProcessCommands.waitForFileCommand(markerFile)
      val blockingFuture = shellCommunication.runAndCollectOutput(blockingCommand)

      // Step 2: Start soft restart (it blocks waiting for queue to empty)
      import scala.concurrent.ExecutionContext.Implicits.global
      Future {
        val future = shellCommunication.runAfterSoftRestart(SbtShellCommandRequest.collectOutput("task", restartRequestId))
        restartFuture.set(future)
      }

      // Step 3: Wait until shell enters SoftRestarting state
      AwaitTestUtils.waitForConditionOrFail(20.seconds, "Shell did not enter soft restart process") {
        () => shellCommunication.currentState.isSoftRestarting
      }

      // Step 4: Release the blocking command by creating the marker file
      Files.writeString(markerFile, "release")

      checker.awaitSuccessful(20.seconds)

      AwaitTestUtils.waitForConditionOrFail(20.seconds, "Future for the after-restart command is null") {
        () => restartFuture.get() != null
      }

      // Step 5: Wait for all futures to complete
      val futures = Future.sequence(List(blockingFuture, restartFuture.get(), taskFuture.get()))
      Await.result(futures, commandWaitTimeout)

      val events = shellCommunication.diagnosticEventsSnapshot
      val snapshot = shellCommunication.diagnosticsSnapshot

      assertDiagnosticEventExists[EnqueueAfterRestartCommands](events, taskId, snapshot)
      assertDiagnosticEventExists[ProcessCommandStart](events, taskId, snapshot)

      assertDiagnosticEventExists[EnqueueAfterRestartCommands](events, restartRequestId, snapshot)
      assertDiagnosticEventExists[ProcessCommandStart](events, restartRequestId, snapshot)
    } finally {
      shellCommunication.removeTestStateListener(submitListener)
      // release waiting for mock command
      Files.writeString(markerFile, "release")
    }
  }

  /**
   * Verifies that when multiple commands are buffered in the standard command queue
   * and in the `afterRestartCommands` queue, and a hard kill is executed
   * (e.g., by pressing the Stop button in the sbt shell), all commands from both queues are terminated.
   * Additionally, no commands remain in either queue.
   */
  def testHardKill_DuringSoftRestart_ClearsBothQueues(): Unit = {
    val markerFile = getTestProjectPath.resolve(s"${getTestName(false)}.marker")

    try {
      val checker = StateSequenceChecker.start(
        ShellState.Idle,
        ShellState.Queued,
        ShellState.SoftRestarting,
        ShellState.ShuttingDown,
        ShellState.Off
      )

      // Block shell
      val blockingFuture = shellCommunication.runAndCollectOutput(MockSbtProcessCommands.waitForFileCommand(markerFile))

      // Initiate soft restart
      val restartRequestId = SbtShellCommandRequestId("soft-restart-command")
      val restartFutureRef = new AtomicReference[Future[StringBuilder]](null)

      import scala.concurrent.ExecutionContext.Implicits.global
      Future {
        val future = shellCommunication.runAfterSoftRestart(SbtShellCommandRequest.collectOutput("task", restartRequestId))
        restartFutureRef.set(future)
      }

      // Wait for soft restart state
      AwaitTestUtils.waitForConditionOrFail(20.seconds, "Shell did not enter soft restart") {
        () => shellCommunication.currentState.isSoftRestarting
      }

      // Submit 2 commands (routed to afterRestartCommands)
      val afterRestartId1 = SbtShellCommandRequestId("after-restart-1")
      val afterRestartFuture1 = shellCommunication.run(SbtShellCommandRequest.collectOutput("task", afterRestartId1))

      val afterRestartId2 = SbtShellCommandRequestId("after-restart-2")
      val afterRestartFuture2 = shellCommunication.run(SbtShellCommandRequest.collectOutput("task", afterRestartId2))

      SbtProcessManager.forProject(project).destroyProcess()

      checker.awaitSuccessful(20.seconds)

      // Wait for restart future to be set
      AwaitTestUtils.waitForConditionOrFail(20.seconds, "Restart future not set") {
        () => restartFutureRef.get() != null
      }

      // Verify futures failed
      Seq(blockingFuture.value, restartFutureRef.get().value, afterRestartFuture1.value, afterRestartFuture2.value).foreach {
        case Some(Failure(_)) => // expected
        case other => fail(s"blockingFuture should fail, but got: $other")
      }

      val events = shellCommunication.diagnosticEventsSnapshot
      val snapshot = shellCommunication.diagnosticsSnapshot

      assertDiagnosticEventExists[EnqueueAfterRestartCommands](events, restartRequestId, snapshot)
      assertCommandWasTerminatedInDiagnostics(restartRequestId, events, snapshot)

      assertDiagnosticEventExists[EnqueueAfterRestartCommands](events, afterRestartId1, snapshot)
      assertCommandWasTerminatedInDiagnostics(afterRestartId1, events, snapshot)

      assertDiagnosticEventExists[EnqueueAfterRestartCommands](events, afterRestartId2, snapshot)
      assertCommandWasTerminatedInDiagnostics(afterRestartId2, events, snapshot)

      assertTrue(
        s"After restart commands queue should be empty after hard kill, but has ${shellCommunication.afterRestartCommands.size()} commands",
        shellCommunication.afterRestartCommands.size() == 0
      )
      assertTrue(
        s"Commands queue should be empty after hard kill, but has ${shellCommunication.afterRestartCommands.size()} commands",
        shellCommunication.commands.size() == 0
      )

    } finally {
      Files.writeString(markerFile, "release")
    }
  }

  private def assertCommandWasTerminatedInDiagnostics(
    requestId: SbtShellCommandRequestId,
    events: Seq[SbtShellDiagnosticEvent],
    snapshot: String
  ): Unit = {
    assertDiagnosticEventExists[TerminatePendingCommand](events, requestId, snapshot)

    assertDiagnosticEventNotExists[ProcessCommandStart](events, requestId, snapshot)
    assertDiagnosticEventNotExists[ProcessCommandFinish](events, requestId, snapshot)
  }
}
