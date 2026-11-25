package org.jetbrains.sbt.shell

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.{JavaModuleTestCase, PlatformTestUtil, TestActionEvent}
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.sbt.process.SbtProcessOutputDiagnosticsCollector
import org.jetbrains.sbt.process.mock.MockSbtProcessForTestsSetup
import org.jetbrains.sbt.project.fixture.TestProjectJdkHolder
import org.jetbrains.sbt.shell.action.EOFAction
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.concurrent.duration.*

/**
 * Integration test base for the EOF action (Ctrl + D) in the sbt shell, with the mock sbt process.
 *
 * It verifies:
 *  1. The EOF action terminates the sbt shell
 *  2. No EDT threading violations occur during shutdown
 *
 * A Windows console in line mode doesn't treat Ctrl+D as end of file, so on Windows the mock reads the console with
 * `ReadConsoleW`, which can also finish a read on Ctrl+D (SCL-26340). [[SbtShellEOFActionRealSbtTestBase]] checks the
 * same with real sbt.
 */
@RunWith(classOf[JUnit4])
abstract class SbtShellEOFActionTestBase extends JavaModuleTestCase {

  protected def useNewShell: Boolean

  private lazy val testProjectJdk = new TestProjectJdkHolder(MockSbtProcessForTestsSetup.MockProcessJdk)

  override def runInDispatchThread(): Boolean = false

  override protected def getTestProjectJdk: Sdk =
    testProjectJdk.configuredJdk

  override protected def setUpProject(): Unit = {
    testProjectJdk.setUp()
    super.setUpProject()
    testProjectJdk.setAsProjectJdk(getProject)
  }

  override def setUp(): Unit = {
    super.setUp()
    PlatformTestUtil.getOrCreateProjectBaseDir(getProject)

    SbtShellTestUtil.setNewSbtShellEnabled(useNewShell, getTestRootDisposable)

    MockSbtProcessForTestsSetup.enableMockSbtProcess(getProject, getTestRootDisposable)
  }

  override def tearDown(): Unit =
    try {
      testProjectJdk.tearDown()
    } finally {
      super.tearDown()
    }

  @Test
  def sbtShellEOFActionStopsMockSbt(): Unit = {
    // Keep only this test's process output for the failure message.
    SbtProcessOutputDiagnosticsCollector.clearSharedProcessOutput()

    // Step 1: Start the sbt shell and wait for it to be ready
    SbtShellTestUtil.waitUntilSbtShellIsReady(
      getProject,
      10.seconds,
      "Timed out waiting for sbt shell to start and be ready"
    )

    // Step 2: Run the EOF action on EDT
    val dataContext = SimpleDataContext.getProjectContext(getProject)
    invokeAndWait {
      new EOFAction(getProject).actionPerformed(TestActionEvent.createTestEvent(dataContext))
    }

    // Step 3: Wait for shell to switch off
    val shellCommunication = SbtShellCommunication.forProject(getProject)
    def isStopped: Boolean = shellCommunication.currentState.isOff && !SbtProcessManager.forProject(getProject).isAlive
    AwaitTestUtils.waitConditioned(20.seconds)(() => isStopped)
    if (!isStopped) {
      val output = SbtProcessOutputDiagnosticsCollector.sharedProcessOutput
      fail(s"Shell state should be Off after sending EOF command (in a 20 seconds time frame)\n$output")
    }
  }
}

class SbtShellEOFActionTest_OldShell extends SbtShellEOFActionTestBase {
  override protected def useNewShell: Boolean = false
}

class SbtShellEOFActionTest_NewShell extends SbtShellEOFActionTestBase {
  override protected def useNewShell: Boolean = true
}
