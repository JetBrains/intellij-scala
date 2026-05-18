package org.jetbrains.sbt.shell

import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.sbt.shell.communication.SbtShellLifecycle.ShellState
import org.junit.experimental.categories.Category

import java.nio.file.Files
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

/**
 * A test class to verify that the sbt shell states are correct in specific scenarios.
 * There is no special teardown method to clean up or kill the sbt shell because, after each test, when the project is closed,
 * `org.jetbrains.sbt.shell.SbtProcessManager#dispose` is called.
 *
 * @todo extend this test class to include checks for whether specific tasks were actually executed in the shell and whether they were successful
 */
@Category(Array(classOf[SlowTests2]))
class SbtShellStateIntegrationTest extends SbtShellRuntimeTestBase {

  override protected def getRelativeTestProjectPath: String = "sbt-shell-runtime-tests/testdata/sbt/shell/testShellState"

  def testAfterStartup(): Unit = {
    val checker = StateSequenceChecker.start(ShellState.Idle)
    checker.await()
  }

  def testSingleCommand(): Unit = {
    val checker = StateSequenceChecker.start(ShellState.Idle, ShellState.Queued, ShellState.Idle)

    val future = comm.runAndCollectOutput("task")
    Await.result(future, DefaultCommandWaitTimeout)

    checker.await()
  }

  def testMultipleCommands(): Unit = {
    // The `Queued` state is expected 3 times: first for the first queued task,
    // second for the second task, and third because, when the first task completes,
    // another `Queued` state is emitted by `SbtShellReadyListener#whenReady`, which is created in `SbtShellCommunication.initCommunication`.
    val checker = StateSequenceChecker.start(ShellState.Idle, ShellState.Queued, ShellState.Queued, ShellState.Queued, ShellState.Idle)

    val futures = Seq(
      comm.runAndCollectOutput("task"),
      comm.runAndCollectOutput("task")
    )
    implicit val ec: ExecutionContext = ExecutionContext.global
    Await.result(Future.sequence(futures), DefaultCommandWaitTimeout)

    checker.await()
  }

  def testDestroyProcess(): Unit = {
    val checker = StateSequenceChecker.start(ShellState.Idle, ShellState.ShuttingDown, ShellState.Off)

    SbtProcessManager.forProject(getMyProject).destroyProcess()

    checker.await()
  }

  def testExternalProcessKill(): Unit = {
    val checker = StateSequenceChecker.start(ShellState.Idle, ShellState.ShuttingDown, ShellState.Off)

    shellProcessHandler.destroyProcess()

    checker.await()
  }

  def testRestart(): Unit = {
    val checker = StateSequenceChecker.start(ShellState.Idle, ShellState.ShuttingDown, ShellState.Off, ShellState.Idle)

    SbtProcessManager.forProject(getMyProject).restartProcess()

    checker.await()
  }

  def testRestartShellAfterSbtVersionChange(): Unit = {
    // Due to the current implementation in `SbtShellCommunication.initCommunication`, when the shell starts again (after the `Off` state),
    // the `Queued` state is expected twice. The first is emitted before the shell starts queue processing
    // (inside `SbtShellCommunication.initCommunication`), and the second comes from the
    // `whenReady` listener when the shell becomes ready to proceed with the queued import command. This could be improved in the future.
    // This issue is also mentioned in `org.jetbrains.sbt.shell.SbtShellLifecycle.transition`.
    val checker = StateSequenceChecker.start(ShellState.Idle, ShellState.ShuttingDown, ShellState.Off, ShellState.Queued, ShellState.Queued, ShellState.Idle)

    Files.writeString(
      getTestProjectPath / "project" / "build.properties",
      "sbt.version=1.12.1"
    )
    importProject()

    checker.await()
  }

  /**
   * Verifies that the sbt shell states throughout the whole test match those declared in [[expectedStates]].
   * To achieve this, a listener is registered in [[SbtShellCommunication]] that listens for any state changes.
   */
  class StateSequenceChecker(expectedStates: ShellState*) {
    private val promise = Promise[Unit]()
    @volatile private var index = 0

    // It's done to register first `Idle` state
    listener(comm.currentState)
    comm.setTestStateListener(listener)

    private def listener(state: ShellState): Unit =
      if (!promise.isCompleted && index < expectedStates.length) {
        val expectedState = expectedStates(index)
        if (state != expectedState) {
          promise.tryFailure(new Exception(s"Unexpected state at position $index in sequence: expected $expectedState, but got $state"))
        } else {
          index += 1
          if (index == expectedStates.length)
            promise.trySuccess(())
        }
      }

    /**
     * Blocks the thread until the expected state sequence is fully observed, an unexpected state is encountered, or the timeout expires.
     */
    def await(timeout: Duration = DefaultCommandWaitTimeout): Unit =
      try {
        Await.result(promise.future, timeout)
      } finally {
        comm.clearTestStateListener()
      }
  }

  object StateSequenceChecker {
    def start(expectedStates: ShellState*): StateSequenceChecker =
      new StateSequenceChecker(expectedStates: _*)
  }
}
