package org.jetbrains.sbt.shell

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.sbt.shell.communication.SbtShellCommandRequest
import org.jetbrains.sbt.shell.communication.SbtShellLifecycle.ShellState
import org.junit.experimental.categories.Category

import java.nio.file.Files
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, Promise}

/**
 * A test class to verify that the sbt shell states are correct in specific scenarios.
 * There is no special teardown method to clean up or kill the sbt shell because, after each test, when the project is closed,
 * `org.jetbrains.sbt.shell.SbtProcessManager#dispose` is called.
 *
 * @todo extend this test class to include checks for whether specific tasks were actually executed in the shell and whether they were successful
 */
@Category(Array(classOf[SlowTests2]))
class SbtShellStateIntegrationTest extends SbtRuntimeTest_WithSbtShell {

  override protected def getRelativeTestProjectPath: String = "sbt-shell-runtime-tests/testdata/sbt/shell/testShellState"

  protected def project: Project = getMyProject

  protected def shellCommunication: SbtShellCommunication = super.comm

  protected val commandWaitTimeout: FiniteDuration = DefaultCommandWaitTimeout

  def testAfterStartup(): Unit = {
    val checker = StateSequenceChecker.start(ShellState.Idle)
    checker.await()
  }

  def testSingleCommand(): Unit = {
    val checker = StateSequenceChecker.start(ShellState.Idle, ShellState.Queued, ShellState.Idle)

    val future = shellCommunication.runAndCollectOutput("task")
    Await.result(future, commandWaitTimeout)

    checker.await()
  }

  def testMultipleCommands(): Unit = {
    val checker = StateSequenceChecker.start(
      ShellState.Idle,
      ShellState.Queued,
      ShellState.Queued,
      ShellState.Queued,
      ShellState.Idle
    )

    val firstCommandTextRequested = Promise[Unit]()
    val releaseFirstCommandText = Promise[Unit]()
    val firstCommandRequest = SbtShellCommandRequest.collectOutput {
      // Command text is evaluated after the request is dequeued but before it is written to sbt.
      // Holding it here lets the second command be enqueued deterministically, without relying on task duration.
      firstCommandTextRequested.trySuccess(())
      Await.result(releaseFirstCommandText.future, commandWaitTimeout)
      "task"
    }

    val firstFuture = shellCommunication.runAndCollectOutput(firstCommandRequest)
    Await.result(firstCommandTextRequested.future, commandWaitTimeout)

    val secondFuture =
      try shellCommunication.runAndCollectOutput("task")
      finally releaseFirstCommandText.trySuccess(())

    Await.result(firstFuture, commandWaitTimeout)
    Await.result(secondFuture, commandWaitTimeout)

    checker.await()
  }

  def testDestroyProcess(): Unit = {
    val checker = StateSequenceChecker.start(
      ShellState.Idle,
      ShellState.ShuttingDown,
      ShellState.Off
    )

    SbtProcessManager.forProject(project).destroyProcess()

    checker.await()
  }

  def testExternalProcessKill(): Unit = {
    val checker = StateSequenceChecker.start(
      ShellState.Idle,
      ShellState.ShuttingDown,
      ShellState.Off
    )

    shellProcessHandler.destroyProcess()

    checker.await()
  }

  def testRestart(): Unit = {
    val checker = StateSequenceChecker.start(
      ShellState.Idle,
      ShellState.ShuttingDown,
      ShellState.Off,
      ShellState.Idle
    )

    SbtProcessManager.forProject(project).restartProcess()

    checker.await()
  }

  def testRestartShellAfterSbtVersionChange(): Unit = {
    // This intentionally goes through project import instead of calling `SbtShellCommunication.runAfterSoftRestart` directly.
    // In production, the sbt-version-change restart decision currently lives in `SbtStructureDumper.FromShell`: during
    // shell-based project reload/import it checks `SbtProcessManager.isSbtVersionOutdated` and only then calls
    // `runAfterSoftRestart`. The mock-sbt variant follows the same path; its mock process writes a minimal hardcoded
    // structure XML for the `dumpStructureTo` command so the import can complete without a real sbt build.
    // After the old shell process stops, the queued import command is picked up only after the restarted shell reports
    // a real ready prompt. `initCommunication` must not emit a synthetic queue state before that prompt.
    val checker = StateSequenceChecker.start(
      ShellState.Idle,
      ShellState.ShuttingDown,
      ShellState.Off,
      ShellState.Queued,
      ShellState.Idle
    )

    Files.writeString(
      getTestProjectPath / "project" / "build.properties",
      "sbt.version=1.12.1"
    )
    importProject()

    checker.await()
  }

  /**
   * Verifies that the sbt shell states throughout the whole test match the declared expected sequence.
   * To achieve this, a listener is registered in [[SbtShellCommunication]] that listens for any state changes.
   */
  class StateSequenceChecker(expectedStates: Seq[ShellState]) {
    private val promise = Promise[Unit]()
    @volatile private var index = 0

    // Replay the current state so the checker observes the initial Idle state that happened before listener registration.
    listener(shellCommunication.currentState)
    shellCommunication.setTestStateListener(listener)

    private def listener(state: ShellState): Unit =
      if (!promise.isCompleted) {
        if (index >= expectedStates.length || expectedStates(index) != state) {
          val expectedState = expectedStates.lift(index).fold("<end of sequence>")(_.toString)
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
    def await(timeout: Duration = commandWaitTimeout): Unit =
      try {
        Await.result(promise.future, timeout)
      } finally {
        shellCommunication.clearTestStateListener()
      }
  }

  object StateSequenceChecker {
    def start(expectedStates: ShellState*): StateSequenceChecker =
      new StateSequenceChecker(expectedStates)
  }
}
