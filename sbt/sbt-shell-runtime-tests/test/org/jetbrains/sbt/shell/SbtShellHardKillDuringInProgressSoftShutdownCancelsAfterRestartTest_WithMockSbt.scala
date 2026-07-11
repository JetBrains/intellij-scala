 package org.jetbrains.sbt.shell

import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.sbt.process.mock.MockSbtProcessForTestsSetup
import org.jetbrains.sbt.shell.communication.SbtShellLifecycle.ShellState
import org.jetbrains.sbt.shell.communication.{SbtShellCommandRequest, SbtShellCommandRequestId}
import org.junit.Assert.assertTrue
import org.junit.experimental.categories.Category

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.DurationInt

/**
 * A hard kill (Stop) that arrives while a soft shutdown is already in progress must still take effect: it should cancel
 * the commands buffered for the soft restart so the shell does NOT come back after the current shutdown completes.
 *
 * This covers the coalescing branch of [[SbtProcessManager.prepareDestroyProcess]]:
 * a concurrent `destroyProcess()` returns without blocking (that is what avoids the SCL-25654 deadlock,
 * see [[SbtShellConcurrentDestroyProcessDoesNotWaitForCurrentShutdownTest_WithSlowMockSbt]]),
 * but a hard kill must not be silently dropped.
 *
 * The hard kill is fired from a state listener at the exact moment the soft destruction transitions to [[ShellState.ShuttingDown]].
 * At that point the soft destruction has already marked itself "in progress"
 * (so the hard `destroyProcess()` coalesces), and the after-restart command is still buffered,
 * which makes the ordering deterministic without depending on the background command-queue thread's timing.
 */
@Category(Array(classOf[SlowTests2]))
class SbtShellHardKillDuringInProgressSoftShutdownCancelsAfterRestartTest_WithMockSbt extends SbtRuntimeTest_WithSbtShell {

  override protected def getRelativeTestProjectPath: String =
    "sbt-shell-runtime-tests/testdata/sbt/shell/testShellState"

  override protected def importProjectDuringTestSetUp: Boolean = false

  override protected def useNewShell: Boolean = true

  override protected def setupBeforeProjectImport(): Unit = {
    super.setupBeforeProjectImport()
    MockSbtProcessForTestsSetup.enableMockSbtProcess(getMyProject, getTestRootDisposable)
  }

  override def setUp(): Unit = {
    super.setUp()
    sbtShellFixture.waitForShellReady(project)
  }

  private def project = getMyProject

  private val commandWaitTimeout = DefaultCommandWaitTimeout

  def testHardKillDuringInProgressSoftShutdown_CancelsAfterRestartCommands(): Unit = {
    val restartRequestId = SbtShellCommandRequestId("soft-restart-command")

    // Fire a hard kill (Stop) exactly when the in-progress soft destroy enters ShuttingDown.
    val hardKillFired = new AtomicBoolean(false)
    val hardKillOnShutdown: ShellState => Unit = { state =>
      if (state == ShellState.ShuttingDown && hardKillFired.compareAndSet(false, true)) {
        SbtProcessManager.forProject(project).destroyProcess()
      }
    }
    shellCommunication.addTestStateListener(hardKillOnShutdown)

    try {
      val restartFuture = shellCommunication.runAfterSoftRestart(SbtShellCommandRequest.collectOutput("task", restartRequestId))

      // The buffered after-restart command must have been terminated by the hard kill, not executed by a restarted shell.
      AwaitTestUtils.waitForConditionOrFail(commandWaitTimeout, "After-restart command future did not complete") { () =>
        restartFuture.isCompleted
      }
      assertTrue(
        s"Hard kill during an in-progress soft shutdown must cancel the buffered after-restart command, but it completed with: ${restartFuture.value}",
        restartFuture.value.exists(_.isFailure)
      )

      // ...and the shell must stay off instead of restarting.
      AwaitTestUtils.waitForConditionOrFail(10.seconds, s"Expected sbt shell to stay off after a hard kill. ${shellCommunication.diagnosticsSnapshot}") { () =>
        shellCommunication.currentState == ShellState.Off && !SbtProcessManager.forProject(project).isAlive
      }
    } finally {
      shellCommunication.removeTestStateListener(hardKillOnShutdown)
    }
  }
}
