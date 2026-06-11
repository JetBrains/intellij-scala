package org.jetbrains.sbt.runner

import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.sbt.SbtRuntimeTestBase
import org.jetbrains.sbt.runner.TestExecutionOptions.ExecutionMode
import org.jetbrains.sbt.runner.utils.{RunConfigInTestsExecutor, RunConfigurationExecutionObserver, SbtRunConfigurationTestFactory}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.experimental.categories.Category

import java.nio.file.{Files, Path}
import scala.concurrent.duration.DurationInt

/**
 * Real-sbt integration coverage for command text semantics of SBT run configurations.
 *
 * See other more lightweight tests:
 *  - Coverage for persisted `tasks`/`commands` conversion without running sbt.
 *     - [[SbtRunConfigurationMigrationTest]]
 *     - [[SbtRunConfigurationMigrationReversibilityTest]]
 *  - Test with the mocked sbt process (extend [[SbtRunConfiguration_MockedProcess_ExecutionTestBase]])
 *     - [[SbtRunConfiguration_ExecutionEventsPublishingTestBase]],
 *     - [[beforeLaunch.SbtRunConfiguration_BuildBeforeLaunch_TestBase]]
 *     - [[beforeLaunch.SbtTask_BeforeLaunchStep_AsSbtRunConfiguration_TestBase]]
 *
 * For many run-configuration tests the mocked sbt process is enough: runner selection, process lifecycle events,
 * before-launch behavior, shell-delegated exit codes, listener forwarding, and Java command-line plumbing.
 * Use real sbt in integration tests when the assertion depends on inner sbt process execution logic, for example,
 * how sbt parses and executes submitted commands, or when sbt generates files/output that the test expects.
 */
@Category(Array(classOf[SlowTests2]))
abstract class SbtRunConfigurationExecutionIntegrationTestBase extends SbtRuntimeTestBase {

  override def runInDispatchThread(): Boolean =
    false

  override protected def getRelativeTestProjectPath: String =
    "sbt-shell-runtime-tests/testdata/sbt/runner/runConfigurationExecution"

  protected def useSbtShellInRunConfig: Boolean

  def testRunConfigurationPreservesTestOnlyStyleArgumentsWithQuotedTestFilter(): Unit =
    assertRunConfigurationCommand(
      command = """recordRunConfigurationCommand testOnly org.example.FooSpec -- -z "my test"""",
      expectedInvocations = Seq(Seq("testOnly", "org.example.FooSpec", "--", "-z", "my test"))
    )

  def testRunConfigurationPreservesSemicolonSeparatedCommandsWithArguments(): Unit =
    assertRunConfigurationCommand(
      command = """recordRunConfigurationCommand first; recordRunConfigurationCommand second "two words"""",
      expectedInvocations = Seq(Seq("first"), Seq("second", "two words"))
    )

  def testRunConfigurationPreservesSingleCommandWithQuotedArgument(): Unit =
    assertRunConfigurationCommand(
      command = """recordRunConfigurationCommand alpha "two words" omega""",
      expectedInvocations = Seq(Seq("alpha", "two words", "omega"))
    )

  private def assertRunConfigurationCommand(
    command: String,
    expectedInvocations: Seq[Seq[String]]
  ): Unit = {
    Files.deleteIfExists(outputFile)

    val testName = getTestName(false)
    val runConfigAndSettings = SbtRunConfigurationTestFactory.createNewSbtTaskRunConfiguration(
      getMyProject,
      configurationName = s"sbt: $testName",
      sbtCommands = command,
      useSbtShellInRunConfig = useSbtShellInRunConfig,
      workingDir = Some(getTestProjectPath.toString),
    )
    val executionObserver = RunConfigurationExecutionObserver.subscribe(runConfigAndSettings, getTestRootDisposable)

    RunConfigInTestsExecutor.executeTopLevelConfiguration(getMyProject, runConfigAndSettings, ExecutionMode.Run.executor)
    executionObserver.awaitSuccessfulTermination(timeout = 120.seconds)

    val actualOutput = readOutputFile(testName, command, executionObserver)
    assertEquals(
      s"""Unexpected parsed sbt command for '$testName' in $runConfigurationModeDescription mode.
         |Configured command:
         |$command
         |
         |${diagnostics(executionObserver)}""".stripMargin,
      expectedOutput(expectedInvocations),
      actualOutput
    )
  }

  private def readOutputFile(
    name: String,
    command: String,
    executionObserver: RunConfigurationExecutionObserver
  ): String = {
    assertTrue(
      s"""SBT command '$name' did not write the expected output file: $outputFile
         |Configured command:
         |$command
         |
         |${diagnostics(executionObserver)}""".stripMargin,
      Files.exists(outputFile)
    )
    Files.readString(outputFile).replace("\r\n", "\n")
  }

  private def diagnostics(executionObserver: RunConfigurationExecutionObserver): String = {
    (Seq("Run configuration process output:" -> executionObserver.processOutputSnapshot) ++ additionalDiagnostics).collect {
      case (title, output) if output.nonEmpty =>
        s"$title\n$output"
    }.mkString("\n\n")
  }

  protected def additionalDiagnostics: Seq[(String, String)] =
    Seq.empty

  private def outputFile: Path =
    getTestProjectPath.resolve("target").resolve("run-configuration-commands.txt")

  private def runConfigurationModeDescription: String =
    if (useSbtShellInRunConfig) "sbt shell" else "separate sbt process"

  private def expectedOutput(expectedInvocations: Seq[Seq[String]]): String =
    expectedInvocations
      .map(invocation => invocation.mkString("\n") + "\n---\n")
      .mkString
}

class SbtRunConfigurationExecutionIntegrationTest_NoShell
  extends SbtRunConfigurationExecutionIntegrationTestBase {

  override protected def useSbtShellInRunConfig: Boolean =
    false
}

class SbtRunConfigurationExecutionIntegrationTest_SbtShell
  extends SbtRunConfigurationExecutionIntegrationTestBase {

  import com.intellij.openapi.util.Disposer
  import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellTestFixture, SbtShellTestUtil}

  import scala.compiletime.uninitialized

  private var sbtShellFixture: SbtShellTestFixture = uninitialized

  override protected def useSbtShellInRunConfig: Boolean =
    true

  def testFailingRunConfigurationTaskFinishesWithFailureExitCode(): Unit =
    assertRunConfigurationFinishesWithFailureExitCode("failRunConfigurationCommand")

  def testUnknownRunConfigurationCommandFinishesWithFailureExitCode(): Unit =
    assertRunConfigurationFinishesWithFailureExitCode("unknownDummyCommand")

  private def assertRunConfigurationFinishesWithFailureExitCode(sbtCommands: String): Unit = {
    val testName = getTestName(false)
    val runConfigAndSettings = SbtRunConfigurationTestFactory.createNewSbtTaskRunConfiguration(
      getMyProject,
      configurationName = s"sbt: $testName",
      sbtCommands = sbtCommands,
      useSbtShellInRunConfig = true,
      workingDir = Some(getTestProjectPath.toString),
    )
    val executionObserver = RunConfigurationExecutionObserver.subscribe(runConfigAndSettings, getTestRootDisposable)

    RunConfigInTestsExecutor.executeTopLevelConfiguration(getMyProject, runConfigAndSettings, ExecutionMode.Run.executor)
    executionObserver.awaitTermination(expectedExitCode = 1, timeout = 120.seconds)
  }

  override def setUp(): Unit = {
    super.setUp()

    SbtShellTestUtil.setNewSbtShellEnabled(enabled = false, getTestRootDisposable)
    sbtShellFixture = new SbtShellTestFixture(getMyProject)
    Disposer.register(getTestRootDisposable, sbtShellFixture)
    sbtShellFixture.setUp()
    SbtShellTestUtil.waitUntilSbtShellIsReady(
      getMyProject,
      60.seconds,
      "Timed out waiting for sbt shell before running an sbt run configuration"
    )
  }

  override protected def additionalDiagnostics: Seq[(String, String)] =
    Seq("SBT shell output:" -> Option(sbtShellFixture).map(_.getTestSbtShellProcessListener.getLog).getOrElse(""))

  override def tearDown(): Unit = {
    try {
      SbtProcessManager.forProject(getMyProject).destroyProcess()
    } finally {
      super.tearDown()
    }
  }
}
