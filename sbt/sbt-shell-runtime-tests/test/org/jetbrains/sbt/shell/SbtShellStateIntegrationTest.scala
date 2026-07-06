package org.jetbrains.sbt.shell

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.sbt.project.SbtProjectResolver
import org.jetbrains.sbt.shell.communication.SbtShellLifecycle.ShellState
import org.jetbrains.sbt.shell.communication.SbtShellCommandRequest
import org.junit.Assert.assertTrue
import org.junit.experimental.categories.Category

import java.nio.file.{Files, Path}
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.concurrent.{Await, Promise}

/**
 * A test class to verify that the sbt shell states are correct in specific scenarios.
 * There is no special teardown method to clean up or kill the sbt shell because, after each test, when the project is closed,
 * `org.jetbrains.sbt.shell.SbtProcessManager#dispose` is called.
 *
 * See also [[SbtShellStateIntegrationTest_WithMockSbt]] for the same version of the test but a faster one
 *
 * @todo extend this test class to include checks for whether specific tasks were actually executed in the shell and whether they were successful
 */
@Category(Array(classOf[SlowTests2]))
class SbtShellStateIntegrationTest extends SbtRuntimeTest_WithSbtShell {

  override protected def getRelativeTestProjectPath: String = "sbt-shell-runtime-tests/testdata/sbt/shell/testShellState"

  override protected def importProjectDuringTestSetUp: Boolean = false
  override def runInDispatchThread(): Boolean = false

  protected def project: Project = getMyProject

  protected val commandWaitTimeout: FiniteDuration = DefaultCommandWaitTimeout

  override def setUp(): Unit = {
    super.setUp()

    sbtShellFixture.waitForShellReady(project)
  }

  def testAfterStartup(): Unit = {
    val checker = StateSequenceChecker.start(
      ShellState.Idle
    )
    checker.awaitSuccessful()
  }

  def testSingleCommand(): Unit = {
    val checker = StateSequenceChecker.start(
      ShellState.Idle,
      ShellState.Queued,
      ShellState.Idle
    )

    val future = shellCommunication.runAndCollectOutput("task")
    Await.result(future, commandWaitTimeout)

    checker.awaitSuccessful()
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

    checker.awaitSuccessful()
  }

  def testDestroyProcess(): Unit = {
    val checker = StateSequenceChecker.start(
      ShellState.Idle,
      ShellState.ShuttingDown,
      ShellState.Off
    )

    SbtProcessManager.forProject(project).destroyProcess()

    checker.awaitSuccessful()
  }

  def testExternalProcessKill(): Unit = {
    val checker = StateSequenceChecker.start(
      ShellState.Idle,
      ShellState.ShuttingDown,
      ShellState.Off
    )

    shellProcessHandler.destroyProcess()

    checker.awaitSuccessful()
  }

  def testRestart(): Unit = {
    val checker = StateSequenceChecker.start(
      ShellState.Idle,
      ShellState.ShuttingDown,
      ShellState.Off,
      ShellState.Idle
    )

    SbtProcessManager.forProject(project).restartProcess()

    checker.awaitSuccessful(20.seconds)
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
      ShellState.SoftRestarting,
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

    checker.awaitSuccessful()
  }

  def testImportWithDumpStructureToTargetPathContainingSpaces(): Unit = {
    val structureDir = getTestProjectPath / "target" / "sbt structure output"
    Files.createDirectories(structureDir)

    val structureFile = structureDir / "sbt structure.xml"
    Files.deleteIfExists(structureFile)

    importProjectWithStructureFileForTests(structureFile)

    assertTrue(s"Expected sbt shell import to write structure file: $structureFile", Files.isRegularFile(structureFile))
    assertTrue(s"Expected sbt shell import to write non-empty structure file: $structureFile", Files.size(structureFile) > 0)
  }

  private def importProjectWithStructureFileForTests(structureFile: Path): Unit =
    SbtProjectResolver.setStructureFileForTests(structureFile)
    try {
      importProject()
    } finally {
      SbtProjectResolver.clearStructureFileForTests()
    }

  /**
   * Verifies that the sbt shell states throughout the whole test match the declared expected sequence.
   * To achieve this, a listener is registered in [[SbtShellCommunication]] that listens for any state changes.
   */
  class StateSequenceChecker(expectedStates: Seq[ShellState]) {
    // We have to use `Either[AssertionError, Unit]` and can't just use Unit
    // This is because AssertionError is an Error and the promise.failure would wrapp it into a Boxed exception, which we don't need
    private val promise = Promise[Either[AssertionError, Unit]]()
    @volatile private var index = 0

    private val listenerFn: ShellState => Unit = { state =>
      if (!promise.isCompleted) {
        val isUnexpectedState = index >= expectedStates.length || expectedStates(index) != state
        if (isUnexpectedState) {
          val expectedState = expectedStates.lift(index).fold("<end of sequence>")(_.toString)
          val assertionError = new AssertionError(s"Unexpected state at position $index in sequence: expected $expectedState, but got $state")
          promise.trySuccess(Left(assertionError))
        } else {
          index += 1
          val finishMonitoring = index == expectedStates.length
          if (finishMonitoring) {
            promise.trySuccess(Right(()))
          }
        }
      }
    }

    // Replay the current state so the checker observes the initial Idle state that happened before listener registration.
    listenerFn(shellCommunication.currentState)
    shellCommunication.addTestStateListener(listenerFn)

    /**
     * Blocks the thread until the expected state sequence is fully observed, an unexpected state is encountered, or the timeout expires.
     */
    def awaitSuccessful(timeout: Duration = commandWaitTimeout): Unit =
      try {
        Await.result(promise.future, timeout) match {
          case Right(_) => // good, don't need the result
          case Left(ex) => throw ex
        }
      } finally {
        shellCommunication.removeTestStateListener(listenerFn)
      }
  }

  object StateSequenceChecker {
    def start(expectedStates: ShellState*): StateSequenceChecker =
      new StateSequenceChecker(expectedStates)
  }
}
