package org.jetbrains.sbt.shell

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.{JavaModuleTestCase, PlatformTestUtil, TestActionEvent}
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.sbt.process.mock.MockSbtProcessForTestsSetup
import org.jetbrains.sbt.project.fixture.TestProjectJdkHolder
import org.jetbrains.sbt.shell.action.EOFAction

import scala.concurrent.duration.*

/**
 * Integration test base for the EOF action (Ctrl + D) in the sbt shell.
 *
 * It verifies:
 *  1. The EOF action terminates the sbt shell
 *  2. No EDT threading violations occur during shutdown
 */
abstract class SbtShellEOFActionTestBase extends JavaModuleTestCase {

  protected def useNewShell: Boolean

  private lazy val testProjectJdk = new TestProjectJdkHolder(LanguageLevel.JDK_17)

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

  def testSbtShellEOFAction(): Unit = {
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
    AwaitTestUtils.waitForConditionOrFail(
      20.seconds,
      "Shell state should be Off after sending EOF command"
    ) { () =>
      shellCommunication.currentState.isOff && !SbtProcessManager.forProject(getProject).isAlive
    }
  }
}

class SbtShellEOFActionTest_OldShell extends SbtShellEOFActionTestBase {
  override protected def useNewShell: Boolean = false
}

class SbtShellEOFActionTest_NewShell extends SbtShellEOFActionTestBase {
  override protected def useNewShell: Boolean = true
}
