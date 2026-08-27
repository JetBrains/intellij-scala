package org.jetbrains.sbt.process.options

import com.intellij.openapi.progress.EmptyProgressIndicator
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter, LogReporter}
import org.jetbrains.sbt.SbtUtil.defaultLauncherPath
import org.jetbrains.sbt.process.{SbtProcessOutputDiagnosticsCollector, SbtRunner}
import org.jetbrains.sbt.project.SbtExternalSystemManager
import org.junit.Assert.fail

import java.nio.file.Path
import scala.util.{Failure, Success}

class SbtOptionsIntegrationTest_SeparateProcess extends SbtOptionsIntegrationTestBase {

  override protected def getRelativeTestProjectPath: String =
    SbtOptionsProjectRelativePath

  override protected def setupBeforeProjectImport(): Unit = {
    super.setupBeforeProjectImport()
    configureQuotedPathOptionSourcesBeforeImport()
  }

  def testSbtOptionsFromSettingsEnvironmentAndOptionFilesArePassedToSeparateSbtProcess(): Unit = {
    doTestQuotedPathOptionsFromSettingsAndOptionFilesArePassedToSbtProcess()
    doTestOptionModelRegressionPropertiesArePassedToSeparateSbtProcess()
    doTestNoShareAndTimingsOptionsArePassedToSeparateSbtProcess()
  }

  override protected def runSettingExtractionTask(taskName: String): Unit = {
    val executionSettings = SbtExternalSystemManager.executionSettingsFor(getMyProject, getTestProjectPath.toString)
    val processOutputCollector = new SbtProcessOutputDiagnosticsCollector
    val runner = new SbtRunner(Some(processOutputCollector))
    val workingDirectory = Path.of(executionSettings.realProjectPath)

    given BuildReporter = new LogReporter

    val result = runner.runSbt(
      new EmptyProgressIndicator(),
      workingDirectory,
      executionSettings.vmExecutable.toPath,
      executionSettings.vmOptions,
      executionSettings.userSetEnvironment,
      executionSettings.customLauncher.map(_.toPath).getOrElse(defaultLauncherPath),
      executionSettings.sbtOptions,
      Nil,
      taskName,
      s"Running sbt task `$taskName` in a separate sbt process",
      executionSettings.passParentEnvironment
    )

    result match {
      case Success(messages) if messages.status == BuildMessages.OK =>
      case Success(messages) =>
        fail(
          s"""Separate sbt process finished with status ${messages.status} while running task '$taskName'.
             |Captured sbt process output:
             |${processOutputCollector.processOutput}""".stripMargin
        )
      case Failure(ex) =>
        fail(
          s"""Separate sbt process failed while running task '$taskName'.
             |Captured sbt process output:
             |${processOutputCollector.processOutput}
             |Cause: ${ex.getClass.getName}: ${ex.getMessage}""".stripMargin
        )
    }
  }
}
