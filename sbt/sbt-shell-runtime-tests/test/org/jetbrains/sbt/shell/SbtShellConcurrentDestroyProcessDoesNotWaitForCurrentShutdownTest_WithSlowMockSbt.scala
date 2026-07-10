package org.jetbrains.sbt.shell

import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.sbt.process.mock.MockSbtProcessForTestsSetup
import org.jetbrains.sbt.shell.communication.SbtShellLifecycle.ShellState
import org.junit.Assert.assertTrue
import org.junit.experimental.categories.Category

import java.nio.file.{Files, Path}
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

@Category(Array(classOf[SlowTests2]))
class SbtShellConcurrentDestroyProcessDoesNotWaitForCurrentShutdownTest_WithSlowMockSbt extends SbtRuntimeTest_WithSbtShell {

  override protected def getRelativeTestProjectPath: String =
    "sbt-shell-runtime-tests/testdata/sbt/shell/testShellState"

  override protected def importProjectDuringTestSetUp: Boolean = false

  override protected def useNewShell: Boolean = true

  override protected def setupBeforeProjectImport(): Unit = {
    super.setupBeforeProjectImport()
    MockSbtProcessForTestsSetup.enableMockSbtProcess(
      getMyProject,
      getTestRootDisposable,
      slowShutdownReleaseFile = Some(slowShutdownReleaseMarker),
      slowShutdownStartedFile = Some(slowShutdownStartedMarker),
    )
  }

  override def setUp(): Unit = {
    super.setUp()
    sbtShellFixture.waitForShellReady(project)
  }

  private def project = getMyProject

  private val commandWaitTimeout = DefaultCommandWaitTimeout

  private def slowShutdownReleaseMarker: Path =
    getTestProjectPath.resolve(s"${getTestName(false)}.shutdown.release")

  private def slowShutdownStartedMarker: Path =
    getTestProjectPath.resolve(s"${getTestName(false)}.shutdown.started")

  def testConcurrentDestroyProcess_DoesNotWaitForCurrentShutdown(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val processManager = SbtProcessManager.forProject(project)
    val firstDestroy = Future {
      processManager.destroyProcess()
    }

    AwaitTestUtils.waitForConditionOrFail(10.seconds, "Mock sbt process did not enter slow shutdown") { () =>
      Files.exists(slowShutdownStartedMarker)
    }

    assertTrue("The first destroyProcess call should wait for the slow mock shutdown", firstDestroy.value.isEmpty)

    val secondDestroy = Future {
      processManager.destroyProcess()
    }

    try {
      AwaitTestUtils.waitFutureOrFail(
        secondDestroy,
        2.seconds,
        "waiting for concurrent destroyProcess to return while shutdown is already in progress"
      )
      assertTrue("The initial destroyProcess call should still own the real process termination", firstDestroy.value.isEmpty)
    } finally {
      Files.writeString(slowShutdownReleaseMarker, "release")
    }

    AwaitTestUtils.waitFutureOrFail(firstDestroy, commandWaitTimeout, "waiting for initial destroyProcess to finish")
    AwaitTestUtils.waitForConditionOrFail(10.seconds, s"Expected sbt shell to be off. ${shellCommunication.diagnosticsSnapshot}") { () =>
      shellCommunication.currentState == ShellState.Off && !processManager.isAlive
    }
  }
}
