package org.jetbrains.sbt

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.testFramework.{JavaModuleTestCase, PlatformTestUtil}
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.sbt.SbtRunAnythingProvider.SbtShellCommandString
import org.jetbrains.sbt.process.SbtProcessOutputDiagnosticsCollector
import org.jetbrains.sbt.process.mock.MockSbtProcessForTestsSetup
import org.jetbrains.sbt.project.fixture.TestProjectJdkHolder

import scala.concurrent.duration.*

/**
 * Integration tests for executing sbt commands with [[SbtRunAnythingProvider]].
 * Uses a mocked sbt process, as verifying the exact sbt output is not necessary.
 */
class SbtRunAnythingProviderExecutionTest extends JavaModuleTestCase {

  private lazy val testProjectJdk = new TestProjectJdkHolder(LanguageLevel.JDK_11)

  override def runInDispatchThread() = true

  override def setUp(): Unit = {
    super.setUp()

    testProjectJdk.setUp()
    testProjectJdk.setAsProjectJdk(getProject)

    PlatformTestUtil.getOrCreateProjectBaseDir(getProject)
    MockSbtProcessForTestsSetup.enableMockSbtProcess(getProject, getTestRootDisposable)
  }

  override def tearDown(): Unit =
    try {
      testProjectJdk.tearDown()
    } finally {
      super.tearDown()
    }

  def testExecute_ShouldRunAndProduceOutput(): Unit = {
    ThreadingAssertions.assertEventDispatchThread()

    val command = "help"

    val provider = new SbtRunAnythingProvider()
    val dataContext = SimpleDataContext.getProjectContext(getProject)
    provider.execute(dataContext, SbtShellCommandString(command))

    val expectedOutput = s"[processCommand] command=$command"
    val failMessage = s"Expected to see \"$expectedOutput\" output from sbt"
    AwaitTestUtils.waitForConditionDispatchingEdtEventsOrFail(35.seconds, failMessage) { () =>
      SbtProcessOutputDiagnosticsCollector.sharedProcessOutput.contains(expectedOutput)
    }
  }
}
