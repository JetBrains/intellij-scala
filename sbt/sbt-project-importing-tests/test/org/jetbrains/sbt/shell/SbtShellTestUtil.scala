package org.jetbrains.sbt.shell

import com.intellij.execution.process.{OSProcessHandler, ProcessEvent, ProcessListener}
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.scala.ui.AwaitTestUtils

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration

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

    override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
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
}
