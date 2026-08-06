package org.jetbrains.sbt.shell

import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener, ExternalSystemTaskType}
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.sbt.process.mock.{MockSbtProcessCommands, MockSbtProcessForTestsSetup}
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.runner.consoleOutput.SbtShellToolWindowActivationTestUtil.installSbtShellToolWindowActivationProbe
import org.jetbrains.sbt.SbtRuntimeTestBase
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.experimental.categories.Category

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

@Category(Array(classOf[SlowTests2]))
class SbtShellSyncOutputIntegrationTest_WithMockSbt extends SbtRuntimeTestBase {

  override protected def getRelativeTestProjectPath: String =
    "sbt-shell-runtime-tests/testdata/sbt/shell/testShellState"

  override protected def importProjectDuringTestSetUp: Boolean = false

  override def runInDispatchThread(): Boolean = false

  override protected def getTestSbtProjectSettings =
    super.getTestSbtProjectSettings.copy(useSbtShellForImport = true)

  override protected def setupBeforeProjectImport(): Unit = {
    super.setupBeforeProjectImport()
    MockSbtProcessForTestsSetup.enableMockSbtProcess(getMyProject, getTestRootDisposable)
  }

  def testFreshShellStartupOutputIsMirroredToProjectSyncOutput(): Unit = {
    val activationProbe = installSbtShellToolWindowActivationProbe(
      getMyProject,
      createContentOnFirstGet = true,
      getTestRootDisposable,
    )
    val toolWindowActivationBaseline = activationProbe.snapshot()

    val syncOutput = importProjectAndCollectSyncOutput()

    assertContains(syncOutput, "[debug] started (old-shell)")
    assertContains(syncOutput, MockSbtProcessCommands.WroteStructureOutputPrefix)
    activationProbe.assertUnchangedSince(toolWindowActivationBaseline, "Project sync using sbt shell")
  }

  def testSoftRestartStartupOutputIsMirroredOnceToProjectSyncOutput(): Unit = {
    importProject()

    Files.writeString(
      getTestProjectPath.resolve("project").resolve("build.properties"),
      "sbt.version=1.12.1",
      StandardCharsets.UTF_8,
    )

    val syncOutput = importProjectAndCollectSyncOutput()
    val startupLine = "[debug] started (old-shell)"

    assertContains(syncOutput, startupLine)
    assertContains(syncOutput, MockSbtProcessCommands.WroteStructureOutputPrefix)
    assertEquals(
      s"Soft-restart startup output must be mirrored once. Full sync output:\n$syncOutput",
      1,
      countOccurrences(syncOutput, startupLine),
    )
  }

  private def importProjectAndCollectSyncOutput(): String = {
    val listener = new SyncOutputCollector
    val notificationManager = ExternalSystemProgressNotificationManager.getInstance()
    notificationManager.addNotificationListener(listener)
    try {
      importProject()
    } finally {
      notificationManager.removeNotificationListener(listener)
    }

    listener.output
  }

  private def assertContains(output: String, expected: String): Unit =
    assertTrue(
      s"""Expected sync output to contain:
         |  $expected
         |
         |Actual sync output:
         |$output""".stripMargin,
      output.contains(expected),
    )

  private def countOccurrences(text: String, fragment: String): Int = {
    var count = 0
    var from = 0
    var index = text.indexOf(fragment, from)
    while (index >= 0) {
      count += 1
      from = index + fragment.length
      index = text.indexOf(fragment, from)
    }
    count
  }

  private final class SyncOutputCollector extends ExternalSystemTaskNotificationListener {
    private val outputFragments = new ConcurrentLinkedQueue[String]

    def output: String =
      outputFragments.asScala.mkString

    override def onTaskOutput(id: ExternalSystemTaskId, text: String, outputType: ProcessOutputType): Unit =
      if (id.getProjectSystemId == SbtProjectSystem.Id && id.getType == ExternalSystemTaskType.RESOLVE_PROJECT) {
        outputFragments.add(text)
      }
  }
}
