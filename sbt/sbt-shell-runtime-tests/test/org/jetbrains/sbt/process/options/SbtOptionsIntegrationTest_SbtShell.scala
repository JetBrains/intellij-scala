package org.jetbrains.sbt.process.options

import com.intellij.openapi.util.Disposer
import org.jetbrains.sbt.shell.{SbtShellTestFixture, SbtShellTestUtil}

import scala.compiletime.uninitialized
import scala.concurrent.duration.DurationInt

class SbtOptionsIntegrationTest_SbtShell extends SbtOptionsIntegrationTestBase {

  private val SbtShellInitialisationTimeout = 60.seconds

  override protected def getRelativeTestProjectPath: String =
    SbtOptionsProjectRelativePath

  private var sbtShellFixture: SbtShellTestFixture = uninitialized

  override def setUp(): Unit = {
    super.setUp()

    sbtShellFixture = new SbtShellTestFixture(getMyProject)
    Disposer.register(getTestRootDisposable, sbtShellFixture)
    sbtShellFixture.setUp()
  }

  override protected def setupBeforeProjectImport(): Unit = {
    super.setupBeforeProjectImport()
    configureQuotedPathOptionSourcesBeforeImport()
  }

  def testSbtOptionsFromSettingsEnvironmentAndOptionFilesArePassedToSbtShell(): Unit = {
    waitForShellReady()
    doTestQuotedPathOptionsFromSettingsAndOptionFilesArePassedToSbtProcess()
    doTestOptionModelRegressionPropertiesArePassedToSbtShell()
  }

  override protected def runSettingExtractionTask(taskName: String): Unit = {
    SbtShellTestUtil.awaitFutureWithShellLog(
      sbtShellFixture.getSbtShellCommunication.runAndCollectOutput(taskName),
      SbtShellTestFixture.DefaultCommandWaitTimeout,
      s"running sbt task `$taskName` in sbt shell",
      sbtShellFixture.getTestSbtShellProcessListener
    )
  }

  private def waitForShellReady(): Unit =
    SbtShellTestUtil.waitUntilSbtShellIsReady(
      getMyProject,
      SbtShellInitialisationTimeout,
      "Timed out waiting for sbt shell to initialize before reading quoted path options"
    )
}
