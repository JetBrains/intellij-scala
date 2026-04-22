package org.jetbrains.sbt.shell.build

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.packaging.artifacts.Artifact
import com.intellij.task.ProjectTaskManager
import org.jetbrains.plugins.scala.build.BuildMessages
import org.jetbrains.sbt.shell.SbtShellTestUtil
import org.jetbrains.sbt.shell.build.BuildOverSbtShellTester.BuildOverSbtShellResult

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

  def buildArtifactsAndCaptureOutput(artifacts: Seq[Artifact]): BuildOverSbtShellResult = {
    runBuildAndCaptureOutput(s"Building artifacts (${artifacts.size})") {
      ProjectTaskManager.getInstance(project)
        .build(artifacts: _*)
        .blockingGet(1, TimeUnit.MINUTES)
    }
  }

  def rebuildArtifactsAndCaptureOutput(artifacts: Seq[Artifact]): BuildOverSbtShellResult = {
    runBuildAndCaptureOutput(s"Rebuilding artifacts (${artifacts.size})") {
      ProjectTaskManager.getInstance(project)
        .rebuild(artifacts: _*)
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
    val buildResult = try {
      buildAction
    } finally {
      shellProcessHandler.removeProcessListener(shellOutputLogger)
    }
    val shellOutput = BuildMessages.stripAnsiCodes(shellOutputLogger.getLog)

    BuildOverSbtShellResult(buildResult, shellOutput)
  }
}

object BuildOverSbtShellTester {
  final case class BuildOverSbtShellResult(
    buildResult: ProjectTaskManager.Result,
    sbtShellOutput: String,
  )
}
