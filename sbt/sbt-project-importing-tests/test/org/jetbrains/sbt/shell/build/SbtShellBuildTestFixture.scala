package org.jetbrains.sbt.shell.build

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettingsProfile
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.SbtProjectImportTestUtils
import org.jetbrains.sbt.shell.build.BuildOverSbtShellTester.BuildOverSbtShellResult
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

    val sbtShellOutput = buildResult.sbtShellOutput
    if (buildResult.buildResult.isAborted) {
      fail(s"Delegated sbt build was aborted. Captured sbt shell output:\n$sbtShellOutput")
    }
    if (buildResult.buildResult.hasErrors) {
      // TODO SCL-11525: once sbt-shell build errors are reported in Build tool window reliably, assert structured errors instead.
      fail(s"Delegated sbt build failed. Captured sbt shell output:\n$sbtShellOutput")
    }
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
}
