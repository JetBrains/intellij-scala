package org.jetbrains.sbt.shell.build

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.packaging.artifacts.Artifact
import com.intellij.task.ProjectTask
import com.intellij.util.ExceptionUtil
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettingsProfile
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.SbtProjectImportTestUtils
import org.jetbrains.sbt.shell.build.util.BuildOverSbtShellTester
import org.jetbrains.sbt.shell.build.util.BuildOverSbtShellTester.BuildOverSbtShellResult
import org.junit.Assert.{assertTrue, fail}

import java.nio.file.Path

/**
 * Shared fixture for sbt-shell build-delegation integration tests.
 */
final class SbtShellBuildTestFixture(
  testName: String,
  project: Project,
  testProjectPath: Path,
  importProject: () => Unit,
) {

  private def logLongTestStep(step: String): Unit = {
    // We use println to have faster feedback in the test output if something is hanging
    println(s"[$testName] $step")
  }

  private lazy val buildTester =
    new BuildOverSbtShellTester(project, logLongTestStep)

  private val InvalidJpsScalacOption = "-Y_non_existing_compiler_option_for_jps_compilation"

  @volatile private var compileServerWasRunningBeforeTestStart: Boolean = false
  @volatile private var compileServerStartStackTraceBeforeTestStart: Option[Throwable] = None

  def markCompileServerStateBeforeTestStart(): Unit = {
    val state = CompileServerLauncher.captureRunningServerStateForTests
    compileServerWasRunningBeforeTestStart = state.wasRunning
    compileServerStartStackTraceBeforeTestStart = state.startStackTrace
  }

  /**
   * Guard against accidental JPS Scala compilation:
   * this option is intentionally invalid for the JPS Scala compiler path.
   * If JPS compilation is triggered, the build is expected to fail.
   *
   * Users should assert that there are no build errors later in the test after using this method.
   */
  def injectInvalidJpsScalacOption(module: Module): Unit = {
    val profile = ScalaCompilerSettingsProfile.forModule(module)
    val newSettings = profile.getSettings.copy(
      additionalCompilerOptions = Seq(InvalidJpsScalacOption)
    )
    profile.setSettings(newSettings)
  }

  def prepareProjectAndImport(sbtVersion: SbtVersion, scalaVersion: ScalaVersion): Unit = {
    SbtProjectImportTestUtils.injectVariable(
      testProjectPath / "project" / "build.properties",
      "$SBT_VERSION$",
      sbtVersion.toString,
    )
    SbtProjectImportTestUtils.injectVariable(
      testProjectPath / "build.sbt",
      "$SCALA_VERSION$",
      scalaVersion.minor,
    )

    logLongTestStep("Importing project...")
    importProject()
  }

  def assertBuildSuccessful(buildResult: BuildOverSbtShellResult): Unit = {
    if (buildResult == null) {
      fail("Build result is null")
    }

    val diagnostics = renderBuildDiagnostics(buildResult)
    if (buildResult.buildRunResult.buildResult.isAborted) {
      fail(s"Delegated sbt build was aborted.$diagnostics")
    }
    if (buildResult.buildRunResult.buildResult.hasErrors) {
      fail(s"Delegated sbt build failed.$diagnostics")
    }
    assertCompileServerIsNotRunning()
  }

  def assertBuildFailed(buildResult: BuildOverSbtShellResult): Unit = {
    if (buildResult == null) {
      fail("Build result is null")
    }

    val diagnostics = renderBuildDiagnostics(buildResult)
    if (buildResult.buildRunResult.buildResult.isAborted) {
      fail(s"Delegated sbt build was aborted, but an error result was expected.$diagnostics")
    }
    if (!buildResult.buildRunResult.buildResult.hasErrors) {
      fail(s"Delegated sbt build unexpectedly succeeded, but a failure was expected.$diagnostics")
    }
    assertCompileServerIsNotRunning()
  }

  /**
   * Asserts common sbt-shell output markers for build delegation tests.
   *
   * We always require the "/products" marker because both delegated build flows
   * are expected to use the sbt-shell products query path.
   *
   * For sbt 1.x, we require `"done compiling"` to ensure compilation was executed in shell output.<br>
   * For sbt 2, this marker is not required because advanced caching may reuse outputs without printing it.
   */
  def assertOutputMarkersForSbtVersion(
    buildResult: BuildOverSbtShellResult,
    sbtVersion: SbtVersion,
  ): Unit = {
    val sbtShellOutput = buildResult.sbtShellOutput

    assertTrue(
      s"Sbt shell output should contain marker '/products'. Output:\n$sbtShellOutput",
      sbtShellOutput.contains("/products")
    )

    if (!sbtVersion.isSbt2) {
      assertTrue(
        "Sbt shell output should contain 'done compiling' after delegated sbt build is finished",
        sbtShellOutput.contains("done compiling")
      )
    }
  }

  def buildAllModulesAndCaptureOutput(): BuildOverSbtShellResult =
    buildTester.buildAllModulesAndCaptureOutput()

  def buildModulesAndCaptureOutput(modules: Seq[Module]): BuildOverSbtShellResult =
    buildTester.buildModulesAndCaptureOutput(modules)

  def buildModulesAndCaptureOutput(buildTask: ProjectTask): BuildOverSbtShellResult =
    buildTester.buildModulesAndCaptureOutput(buildTask)

  def buildArtifactsAndCaptureOutput(artifacts: Seq[Artifact]): BuildOverSbtShellResult =
    buildTester.buildArtifactsAndCaptureOutput(artifacts)

  def rebuildArtifactsAndCaptureOutput(artifacts: Seq[Artifact]): BuildOverSbtShellResult =
    buildTester.rebuildArtifactsAndCaptureOutput(artifacts)

  def assertCompileServerIsNotRunning(): Unit = {
    val runningState = CompileServerLauncher.captureRunningServerStateForTests
    val wasRunningBeforeSetup = compileServerWasRunningBeforeTestStart
    val isRunningAfterTest = runningState.wasRunning

    if (!wasRunningBeforeSetup && !isRunningAfterTest) return

    val stateMessage = (wasRunningBeforeSetup, isRunningAfterTest) match {
      case (true, true) =>
        "Scala Compile Server state violations detected both before test setup and after test execution."
      case (true, false) =>
        "Scala Compile Server was already running before test setup (pre-existing global state leakage). It is not running after test execution."
      case (false, true) =>
        "Scala Compile Server was started during test execution and is still running after test execution."
      case (false, false) =>
        ""
    }

    val traces = Seq(
      renderStackTraceSection(
        header = "Compile Server start stack trace captured BEFORE test setup",
        throwable = compileServerStartStackTraceBeforeTestStart
      ),
      renderStackTraceSection(
        header = "Compile Server start stack trace captured for server running AFTER test execution",
        throwable = runningState.startStackTrace
      ),
    ).flatten

    val tracesText =
      if (traces.nonEmpty) traces.mkString("\n\n", "\n\n", "")
      else ""

    fail(
      s"""$stateMessage
         |$tracesText""".stripMargin.trim
    )
  }

  private def renderStackTraceSection(header: String, throwable: Option[Throwable]): Option[String] =
    throwable
      .map(ExceptionUtil.getThrowableText)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(stack => s"$header:\n$stack")

  private def renderBuildDiagnostics(buildResult: BuildOverSbtShellResult): String = {
    def renderSection(title: String, messages: Seq[String]): Option[String] =
      Option(messages)
        .filter(_.nonEmpty)
        .map(_.mkString(s"$title:\n  - ", "\n  - ", ""))

    val sections = Seq(
      renderSection("Build tool window errors", buildResult.buildRunResult.buildToolWindowErrors),
      renderSection("Build tool window warnings", buildResult.buildRunResult.buildToolWindowWarnings),
      renderSection("Build tool window root output", buildResult.buildRunResult.buildToolWindowRootOutput),
      renderSection("Build tool window finish failures", buildResult.buildRunResult.buildToolWindowFinishFailures),
      renderSection("CompileContext errors", buildResult.buildRunResult.compilerContextErrors),
      renderSection("CompileContext warnings", buildResult.buildRunResult.compilerContextWarnings),
    ).flatten

    val diagnosticsPrefix =
      if (sections.nonEmpty) sections.mkString("\n", "\n", "\n")
      else "\n"

    s"""${diagnosticsPrefix}Captured sbt shell output:
       |${buildResult.sbtShellOutput}""".stripMargin
  }
}
