package org.jetbrains.sbt.shell

import com.intellij.execution.process.{OSProcessHandler, ProcessEvent, ProcessListener}
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.sbt.shell.communication.SbtShellLifecycle.ShellState

import java.util.concurrent.CountDownLatch
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}

object SbtShellTestUtil {
  val ErrorPrefix = "[error]"

  def setNewSbtShellEnabled(enabled: Boolean, testDisposable: Disposable): Unit = {
    Registry.get("sbt.new.shell").setValue(enabled, testDisposable)
  }

  final class TestSbtShellProcessListener extends ProcessListener {
    private val logBuilder: StringBuilder = new StringBuilder()
    private val termination = Promise.apply[Int]()

    def getLog: String = logBuilder.mkString
    def terminated: Future[Int] = termination.future

    override def processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean): Unit = {}

    override def startNotified(event: ProcessEvent): Unit = {}

    override def processTerminated(event: ProcessEvent): Unit =
      termination.success(event.getExitCode)

    override def onTextAvailable(event: ProcessEvent, outputType: Key[?]): Unit = {
      synchronized {
        logBuilder.append(event.getText)
      }
      print(event.getText)
    }
  }

  def acquireShellProcessHandler(project: Project): OSProcessHandler =
    SbtProcessManager.forProject(project).acquireShellProcessHandler()

  def waitUntilSbtShellIsReady(
    project: Project,
    timeout: FiniteDuration,
    timeoutMessage: String,
  ): OSProcessHandler = {
    // this will start sbt shell
    val shellProcessHandler = acquireShellProcessHandler(project)

    // Wait until sbt shell is initialised and ready for commands
    val shellCommunication = SbtShellCommunication.forProject(project)
    AwaitTestUtils.waitForConditionOrFail(timeout, timeoutMessage) { () =>
      shellCommunication.isRunningAndIdle
    }

    shellProcessHandler
  }

  /**
   * Awaits one shell state observed through the temporary test state listener.
   */
  final class ShellStateAwaiter(
    shellCommunication: SbtShellCommunication,
    latch: CountDownLatch,
    listener: ShellState => Unit,
  ) {
    def await(timeout: FiniteDuration, timeoutMessage: String): Unit =
      try {
        AwaitTestUtils.waitForLatchDispatchingAllEdtEvents(latch, timeout, timeoutMessage)
      } finally {
        dispose()
      }

    def dispose(): Unit = {
      shellCommunication.removeTestStateListener(listener)
    }
  }

  /**
   * Installs a temporary listener that completes when the sbt shell next enters the queued state.
   */
  def observeNextQueuedState(project: Project): ShellStateAwaiter = {
    val shellCommunication = SbtShellCommunication.forProject(project)
    val latch = new CountDownLatch(1)
    val listener: ShellState => Unit = {
      case ShellState.Queued => latch.countDown()
      case _ =>
    }
    shellCommunication.addTestStateListener(listener)
    new ShellStateAwaiter(shellCommunication, latch, listener)
  }

  def awaitFutureWithShellLog[T](
    future: Future[T],
    timeout: FiniteDuration,
    actionDescription: String,
    processListener: TestSbtShellProcessListener,
  ): T = {
    AwaitTestUtils.waitFutureOrFail(
      future,
      timeout,
      s"$actionDescription.${shellLogSuffix(processListener)}"
    )
  }

  private def shellLogSuffix(processListener: TestSbtShellProcessListener): String = {
    val log = Option(processListener)
      .map(_.getLog)
      .filter(_.nonEmpty)
      .getOrElse("<empty sbt shell log>")

    s"\nCaptured sbt shell log:\n$log"
  }
}
