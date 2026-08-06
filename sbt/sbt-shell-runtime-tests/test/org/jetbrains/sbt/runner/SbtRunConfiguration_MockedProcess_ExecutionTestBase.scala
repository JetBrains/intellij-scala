package org.jetbrains.sbt.runner

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.{JavaModuleTestCase, PlatformTestUtil}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.compiler.testUtils.{ScalaCompileServerTester, SimpleCompilerTester}
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.process.mock.MockSbtProcessForTestsSetup
import org.jetbrains.sbt.project.fixture.TestProjectJdkHolder
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.runner.TestExecutionOptions.SbtProcessMode
import org.jetbrains.sbt.runner.utils.{ExecutionDiagnostics, RunConfigurationExecutionObserver}
import org.jetbrains.sbt.settings.SbtSettings
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellTestUtil}
import org.junit.Assert.{assertFalse, assertTrue}

import java.nio.file.Path
import scala.compiletime.uninitialized
import scala.concurrent.duration.{Duration, DurationInt}

/**
 * Shared light fixture for SBT run-configuration execution tests.
 *
 * The base sets up the shared infrastructure those tests need: a project JDK, the Scala SDK and libraries,
 * the compile server, and a minimal external-compiler model (see
 * [[org.jetbrains.plugins.scala.compiler.testUtils.SimpleCompilerTester]]).
 *
 * It deliberately does not import a real SBT project;
 * Instead `setUp` substitutes a lightweight `MockSbtProcess` JVM for the real sbt launcher
 * (see [[org.jetbrains.sbt.process.mock.MockSbtProcessForTestsSetup.enableMockSbtProcess]])
 */
abstract class SbtRunConfiguration_MockedProcess_ExecutionTestBase extends JavaModuleTestCase with ScalaSdkOwner {
  import SbtRunConfiguration_MockedProcess_ExecutionTestBase.*

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  override def runInDispatchThread(): Boolean = false

  // Some light execution scenarios start application or Build before-launch tasks and need compiler services available.
  private val scalaCompileServerTester: ScalaCompileServerTester = new ScalaCompileServerTester(
    reuseCompileServerProcessBetweenTests = true,
    compileServerShutdownTimeout = Duration.Inf
  )
  private var simpleCompilerTester: SimpleCompilerTester = uninitialized

  private lazy val testProjectJdk: TestProjectJdkHolder = new TestProjectJdkHolder(testProjectJdkVersion)

  override protected def getProjectLanguageLevel: LanguageLevel =
    testProjectJdkVersion

  override protected def getTestProjectJdk: Sdk =
    testProjectJdk.configuredJdk

  override protected def setUpProject(): Unit = {
    testProjectJdk.setUp()
    super.setUpProject()
    testProjectJdk.setAsProjectJdk(getProject)
  }

  override def setUpModule(): Unit = {
    super.setUpModule()
    setUpLibraries(getModule)
  }

  override protected def setUp(): Unit = {
    super.setUp()

    scalaCompileServerTester.setUp()

    simpleCompilerTester = new SimpleCompilerTester(getProject, getModule)
    simpleCompilerTester.setUp()

    // Substitute the mock sbt process for the whole test;
    // the shell flavor is selected per scenario via configureSbtShellMode. The process substitution itself is independent of the shell flavor.
    MockSbtProcessForTestsSetup.enableMockSbtProcess(getProject, getTestRootDisposable)
  }

  override def tearDown(): Unit = {
    simpleCompilerTester.tearDown()
    scalaCompileServerTester.tearDown()

    try {
      disposeLibraries(getModule)
    } finally {
      try {
        testProjectJdk.tearDown()
      } finally {
        super.tearDown()
      }
    }
  }

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(),
    HeavyJDKLoader(testProjectJdkVersion)
  )

  protected def getTestProjectPath: Path =
    PlatformTestUtil.getOrCreateProjectBaseDir(getProject).toNioPath

  protected final def initSbtShellIfNeeded(options: TestExecutionOptions): Unit = {
    options.sbtProcessMode match {
      case SbtProcessMode.Shell(isNewShell) =>
        // Set the production registry that decides old VS new sbt shell.
        // The run configuration's own `useSbtShell` flag is applied separately when the configuration is created (see SbtRunConfigurationTestFactory).
        SbtShellTestUtil.setNewSbtShellEnabled(isNewShell, getTestRootDisposable)

        setSbtShellDebuggingEnabled(enabled = options.enableDebuggingInShell)

        if (options.prestartSbtShell) {
          SbtShellTestUtil.acquireShellProcessHandler(getProject)
        }
      case _ =>
    }
  }

  protected final def setSbtShellDebuggingEnabled(enabled: Boolean): Unit = {
    val workingDir = SbtUtil.getWorkingDirPath(getProject)
    val sbtSettings = SbtSettings.getInstance(getProject)
    val projectSettings = getOrCreateSbtProjectSettings(sbtSettings, workingDir)
    projectSettings.enableDebugSbtShell = enabled
  }

  protected final def sbtShellModeDisplayName(options: TestExecutionOptions): String =
    if (!options.useSbtShellInRunConfig) "non-shell"
    else if (options.useNewSbtShell) "new-shell"
    else "old-shell"

  private def getOrCreateSbtProjectSettings(sbtSettings: SbtSettings, workingDir: String): SbtProjectSettings =
    Option(sbtSettings.getLinkedProjectSettings(workingDir)).getOrElse {
      val settings = SbtProjectSettings.default
      settings.setExternalProjectPath(workingDir)
      sbtSettings.linkProject(settings)
      settings
    }

  protected def tearDownForTestCase(options: TestExecutionOptions): Unit = {
    if (options.useSbtShellInRunConfig) {
      SbtProcessManager.forProject(getProject).destroyProcess()
    }
  }

  protected def waitUntilSbtShellIsReadyIfNeeded(options: TestExecutionOptions): Unit = {
    if (options.useSbtShellInRunConfig && options.prestartSbtShell) {
      SbtShellTestUtil.waitUntilSbtShellIsReady(getProject, 5.seconds, "Can't start sbt shell")
    }
  }

  protected final def clearSbtProcessOutputDiagnostics(): Unit =
    ExecutionDiagnostics.clearSbtProcessOutput()

  protected final def withExecutionDiagnostics[T](
    executionObserver: => Option[RunConfigurationExecutionObserver] = None,
  )(body: => T): T =
    ExecutionDiagnostics.withDiagnostics(executionObserver)(body)

  protected final def assertExpectedDebugOutput(
    options: TestExecutionOptions,
    executionObserver: RunConfigurationExecutionObserver,
  ): Unit = {
    if (!options.expectsRunConfigurationDebugConnection) {
      return
    }

    val runConfigurationOutput = executionObserver.consoleOutputSnapshot
    assertTrue(
      s"""Debug run configuration console output must contain debugger attach output.
         |Expected output fragment:
         |${DebuggerConnectedOutput.indent(2)}Actual run configuration console output:
         |${runConfigurationOutput.indent(2)}""".stripMargin,
      runConfigurationOutput.contains(DebuggerConnectedOutput),
    )
    assertTrue(
      s"""Debug run configuration console output must contain debugger detach output.
         |Expected output fragment:
         |${DebuggerDisconnectedOutput.indent(2)}Actual run configuration console output:
         |${runConfigurationOutput.indent(2)}""".stripMargin,
      runConfigurationOutput.contains(DebuggerDisconnectedOutput),
    )

    if (options.useSbtShellInRunConfig && options.enableDebuggingInShell) {
      assertFalse(
        s"""Debug run configuration console output must not contain sbt shell debug-server startup output.
           |Unexpected output fragment:
           |${DebuggerListeningOutput.indent(2)}Actual run configuration console output:
           |${runConfigurationOutput.indent(2)}""".stripMargin,
        runConfigurationOutput.contains(DebuggerListeningOutput),
      )
    }

    if (options.expectsSbtShellDebugListeningDuringExecution) {
      val sbtProcessOutput = ExecutionDiagnostics.sbtProcessOutputSnapshot
      assertTrue(
        s"""Fresh sbt shell debug execution must contain shell debug-server startup output.
           |Expected output fragment:
           |${DebuggerListeningOutput.indent(2)}Actual SBT process output:
           |${sbtProcessOutput.indent(2)}""".stripMargin,
        sbtProcessOutput.contains(DebuggerListeningOutput),
      )
    }
  }
}

private object SbtRunConfiguration_MockedProcess_ExecutionTestBase {
  private val DebuggerConnectedOutput = "Connected to the target VM"
  private val DebuggerDisconnectedOutput = "Disconnected from the target VM"
  private val DebuggerListeningOutput = "Listening for transport dt_socket at address:"
}
