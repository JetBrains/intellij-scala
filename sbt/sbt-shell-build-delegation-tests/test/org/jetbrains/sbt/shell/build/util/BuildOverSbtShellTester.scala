package org.jetbrains.sbt.shell.build.util

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.packaging.artifacts.Artifact
import com.intellij.task.{ProjectTask, ProjectTaskManager}
import org.jetbrains.plugins.scala.build.BuildDiagnosticsCollector
import org.jetbrains.plugins.scala.build.BuildMessages
import org.jetbrains.sbt.shell.SbtShellTestUtil
import org.jetbrains.sbt.shell.build.util.BuildOverSbtShellTester.{BuildOverSbtShellResult, BuildRunResult}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.DurationInt

/**
 * @note We can't use `com.intellij.testFramework.CompilerTester#make()` in these tests because it uses JPS compilation:<br>
 *       `CompilerManager.make(...) -> CompileDriver -> BuildManager.scheduleBuild(...)`<br>
 *       Ot does not delegate to sbt shell even when `useSbtShellForBuild` is enabled.
 *       To verify sbt-shell build delegation, tests should use `ProjectTaskManager` build APIs,
 *       because runner selection there can route execution to `SbtProjectTaskRunnerImpl`
 */
final class BuildOverSbtShellTester(
  project: Project,
  logStep: String => Unit,
) {

  def buildAllModulesAndCaptureOutput(): BuildOverSbtShellResult = {
    runBuildAndCaptureOutput("Building all modules") {
      ProjectTaskManager.getInstance(project)
        .buildAllModules()
        .blockingGet(1, TimeUnit.MINUTES)
    }
  }

  def buildModulesAndCaptureOutput(modules: Seq[Module]): BuildOverSbtShellResult = {
    val buildTask = ProjectTaskManager.getInstance(project)
      .createModulesBuildTask(modules.toArray, true, true, false)
    buildModulesAndCaptureOutput(buildTask)
  }

  def buildModulesAndCaptureOutput(buildTask: ProjectTask): BuildOverSbtShellResult = {
    runBuildAndCaptureOutput("Building selected modules (pre-created task)") {
      ProjectTaskManager.getInstance(project)
        .run(buildTask)
        .blockingGet(1, TimeUnit.MINUTES)
    }
  }

  def buildArtifactsAndCaptureOutput(artifacts: Seq[Artifact]): BuildOverSbtShellResult = {
    runBuildAndCaptureOutput(s"Building artifacts (${artifacts.size})") {
      ProjectTaskManager.getInstance(project)
        .build(artifacts*)
        .blockingGet(1, TimeUnit.MINUTES)
    }
  }

  def rebuildArtifactsAndCaptureOutput(artifacts: Seq[Artifact]): BuildOverSbtShellResult = {
    runBuildAndCaptureOutput(s"Rebuilding artifacts (${artifacts.size})") {
      ProjectTaskManager.getInstance(project)
        .rebuild(artifacts*)
        .blockingGet(1, TimeUnit.MINUTES)
    }
  }

  private def waitForIdleSbtShell: OSProcessHandler = {
    logStep("Waiting for sbt shell to be in ready state...")
    SbtShellTestUtil.waitUntilSbtShellIsReady(
      project,
      1.minute,
      "sbt shell is not running with a pending command after importProject(false)",
    )
  }

  private def runBuildAndCaptureOutput(
    buildDescription: String
  )(
    buildAction: => ProjectTaskManager.Result
  ): BuildOverSbtShellResult = {
    val shellProcessHandler: OSProcessHandler = waitForIdleSbtShell

    logStep(buildDescription)

    val shellOutputLogger = new SbtShellTestUtil.TestSbtShellProcessListener
    shellProcessHandler.addProcessListener(shellOutputLogger)
    val buildRunResult = try {
      runBuildAndCaptureBuildToolWindowMessages(buildAction)
    } finally {
      shellProcessHandler.removeProcessListener(shellOutputLogger)
    }
    val shellOutput = BuildMessages.stripAnsiCodes(shellOutputLogger.getLog)

    BuildOverSbtShellResult(
      buildRunResult = buildRunResult,
      sbtShellOutput = shellOutput,
    )
  }

  private def runBuildAndCaptureBuildToolWindowMessages(
    buildAction: => ProjectTaskManager.Result
  ): BuildRunResult = {
    val (taskResult, diagnostics) = BuildDiagnosticsCollector.capture(project)(buildAction)

    BuildRunResult(
      buildResult = taskResult,
      buildToolWindowErrors = diagnostics.buildToolWindowErrors,
      buildToolWindowWarnings = diagnostics.buildToolWindowWarnings,
      buildToolWindowRootOutput = diagnostics.buildToolWindowRootOutput,
      buildToolWindowFinishFailures = diagnostics.buildToolWindowFinishFailures,
      compilerContextErrors = diagnostics.compilerContextErrors,
      compilerContextWarnings = diagnostics.compilerContextWarnings,
    )
  }

}

object BuildOverSbtShellTester {
  /**
   * Diagnostics captured around one `ProjectTaskManager` build invocation.
   *
   * @note We intentionally keep both Build Tool Window and `CompileContext` diagnostics:
   *       `buildToolWindowErrors` / `buildToolWindowWarnings` come from Build Tool Window `MessageEvent`s,
   *       while `compilerContextErrors` / `compilerContextWarnings` come from
   *       `CompilationStatusListener` + `CompileContext`. These sources are independent and can diverge
   *       (for example, compiler-side failures may be present only in `CompileContext`).
   * @note We intentionally capture a broad set of diagnostics (`MessageEvent`, root output, finish failures,
   *       `CompileContext`, and sbt shell output), because it's hard to predict in advance which channel will contain
   *       actionable failure details. To keep assertions readable, failure diagnostics render only non-empty sections.
   *
   * @param buildResult                  final project-task result returned by `ProjectTaskManager`
   * @param buildToolWindowErrors        Build Tool Window `MessageEvent` entries with `Kind.ERROR`
   * @param buildToolWindowWarnings      Build Tool Window `MessageEvent` entries with `Kind.WARNING`
   * @param buildToolWindowRootOutput    root-level Build Tool Window output from `OutputBuildEvent`
   * @param buildToolWindowFinishFailures rendered `FailureResult` details from `FinishEvent`
   * @param compilerContextErrors        `CompileContext` error diagnostics captured via `CompilationStatusListener`
   * @param compilerContextWarnings      `CompileContext` warning diagnostics captured via `CompilationStatusListener`
   */
  final case class BuildRunResult(
    buildResult: ProjectTaskManager.Result,
    buildToolWindowErrors: Seq[String],
    buildToolWindowWarnings: Seq[String],
    buildToolWindowRootOutput: Seq[String],
    buildToolWindowFinishFailures: Seq[String],
    compilerContextErrors: Seq[String],
    compilerContextWarnings: Seq[String],
  )

  /**
   * User-facing bundle for delegated sbt-shell test assertions.
   *
   * @param buildRunResult full diagnostics captured from build APIs and compiler callbacks
   * @param sbtShellOutput raw sbt shell stdout/stderr captured during delegated sbt execution
   */
  final case class BuildOverSbtShellResult(
    buildRunResult: BuildRunResult,
    sbtShellOutput: String,
  )
}
