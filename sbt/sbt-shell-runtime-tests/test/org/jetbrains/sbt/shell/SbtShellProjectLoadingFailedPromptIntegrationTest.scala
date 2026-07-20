package org.jetbrains.sbt.shell

import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.plugins.scala.util.RevertableChange
import org.jetbrains.sbt.process.SbtProcessOutputDiagnosticsCollector
import org.jetbrains.sbt.shell.communication.SbtShellLifecycle.ShellState
import org.junit.Assert.{assertEquals, assertFalse, assertTrue, fail}
import org.junit.experimental.categories.Category

import java.nio.file.{Files, Path}
import scala.util.control.NonFatal

/**
 * sbt shell integration tests involving sbt's interactive project loading failure prompt
 * (`Project loading failed: (r)etry, (q)uit, (l)ast, or (i)gnore?`).
 */
abstract class SbtShellProjectLoadingFailedPromptIntegrationTestBase extends SbtRuntimeTest_WithSbtShell {

  private val PrintDiagnosticsOnSuccessProperty =
    "sbt.shell.failed.reload.print.diagnostics.on.success"

  private val ProjectLoadingFailedPrompt = "Project loading failed: (r)etry, (q)uit, (l)ast, or (i)gnore?"

  override protected def getRelativeTestProjectPath: String =
    s"sbt-shell-runtime-tests/testdata/sbt/shell/${getTestName(true)}"

  override protected def importProjectDuringTestSetUp: Boolean = false

  protected final def assertProjectImportFails(failureReason: String, structureFiles: Seq[Path] = Seq.empty): AssertionError = {
    var importFailure: AssertionError = null
    try importProject()
    catch case error: AssertionError => importFailure = error

    if (importFailure == null) {
      fail(withFailedReloadDiagnostics(s"Import must fail when $failureReason", importFailure = None, structureFiles))
      throw new AssertionError("unreachable")
    }

    importFailure
  }

  protected final def withFailedReloadDiagnostics(
    message: String,
    importFailure: AssertionError,
    structureFiles: Seq[Path] = Seq.empty,
  ): String =
    withFailedReloadDiagnostics(message, Some(importFailure), structureFiles)

  protected final def printDiagnosticsOnSuccessIfEnabled(
    importFailure: AssertionError,
    structureFiles: Seq[Path] = Seq.empty,
  ): Unit =
    if (java.lang.Boolean.getBoolean(PrintDiagnosticsOnSuccessProperty)) {
      println(failedReloadDiagnostics(Some(importFailure), structureFiles))
    }

  private def withFailedReloadDiagnostics(
    message: String,
    importFailure: Option[AssertionError],
    structureFiles: Seq[Path],
  ): String =
    s"""$message
       |
       |${failedReloadDiagnostics(importFailure, structureFiles)}""".stripMargin

  private def failedReloadDiagnostics(
    importFailure: Option[AssertionError],
    structureFiles: Seq[Path],
  ): String =
    Seq(
      diagnosticSection("Caught import failure", importFailure.fold("<import did not fail>")(renderThrowable)),
      diagnosticSection("Captured sbt shell log", processListener.getLog),
      diagnosticSection("Shared raw sbt process output", SbtProcessOutputDiagnosticsCollector.sharedProcessOutput),
      diagnosticSection("Sbt shell communication snapshot", shellCommunication.diagnosticsSnapshot),
      diagnosticSection("Structure files", renderStructureFiles(structureFiles)),
    ).mkString("\n")

  private def diagnosticSection(title: String, body: String): String = {
    val text = Option(body).filter(_.nonEmpty).getOrElse("<empty>")
    s"""===== $title =====
       |$text""".stripMargin
  }

  private def renderThrowable(error: Throwable): String = {
    val message = Option(error.getMessage).getOrElse("<no message>")
    val stack = error.getStackTrace.take(60).map(element => s"  at $element").mkString("\n")
    s"${error.getClass.getName}: $message\n$stack"
  }

  private def renderStructureFiles(structureFiles: Seq[Path]): String =
    if (structureFiles.isEmpty) "<none>"
    else structureFiles.map(renderStructureFile).mkString("\n")

  private def renderStructureFile(path: Path): String = {
    val exists = Files.exists(path)
    val regularFile = Files.isRegularFile(path)
    val size =
      if (!regularFile) "<not a regular file>"
      else {
        try Files.size(path).toString
        catch case NonFatal(error) => s"<failed to read size: ${error.getClass.getName}: ${error.getMessage}>"
      }
    s"$path (exists=$exists, regularFile=$regularFile, size=$size)"
  }

  /**
   * Verifies the sbt-shell import path after real sbt fails project reload before structure dumping.
   *
   * The test intentionally makes `build.sbt` invalid, so sbt shows its interactive project-loading failure prompt during
   * shell-based import. IntelliJ must answer that prompt with `i`gnore, report import failure, return the shell to
   * `Idle`, and skip `dumpStructureTo` so no stale or partial structure XML is consumed after the failed reload.
   *
   *
   * @see SCL-25342
   * @see SCL-24349
   * @see SCL-24706
   */
  def testFailedReloadSendsIgnoreAndSkipsDumpStructure(): Unit = {
    sbtShellFixture.waitForShellReady(getMyProject)

    Files.writeString(
      getTestProjectPath / "build.sbt",
      """scalaVersion := "2.13.18"
        |
        |intentionalSbtReloadFailureForSbtShellFailedReloadIntegrationTest + "This is an intentional test failure for SbtShellFailedReloadIntegrationTest"
        |""".stripMargin
    )

    val structureDir = getTestProjectPath / "target" / "failed-reload-structure"
    Files.createDirectories(structureDir)

    val structureFile = structureDir / s"sbt-structure-reused-${getTestProjectPath.getFileName}.xml"
    Files.deleteIfExists(structureFile)

    val forceStructureOutputPath =
      RevertableChange.withModifiedSystemProperty("sbt.project.structure.write", "true") |+|
        RevertableChange.withModifiedSystemProperty("sbt.project.structure.location", structureDir.toString)

    forceStructureOutputPath.applyChange()
    val importFailure =
      try {
        assertProjectImportFails("real sbt reports a project loading failure", Seq(structureFile))
      } finally {
        forceStructureOutputPath.revertChange()
      }

    val log = processListener.getLog

    assertTrue(
      withFailedReloadDiagnostics(
        "Expected real sbt to print the project loading failure prompt",
        importFailure,
        Seq(structureFile),
      ),
      log.contains(ProjectLoadingFailedPrompt)
    )
    assertTrue(
      withFailedReloadDiagnostics(
        "Expected real sbt to consume ignore input and continue after the project loading failure",
        importFailure,
        Seq(structureFile),
      ),
      log.contains("Ignoring load failure")
    )
    assertEquals(
      withFailedReloadDiagnostics(
        "Expected sbt shell to continue after the failed reload prompt instead of waiting for user input",
        importFailure,
        Seq(structureFile),
      ),
      ShellState.Idle,
      shellCommunication.currentState
    )
    assertFalse(
      withFailedReloadDiagnostics(
        s"dumpStructureTo must not write structure XML after reload failed: $structureFile",
        importFailure,
        Seq(structureFile),
      ),
      Files.exists(structureFile)
    )

    printDiagnosticsOnSuccessIfEnabled(importFailure, Seq(structureFile))
  }

  /**
   * When the project loading failed prompt is displayed while
   * the shell is already shutting down, no `ignore` command should be sent, as it would trigger an unwanted shell restart.
   *
   * @see SCL-25058
   */
  def testDoNotSendIgnoreWhenShellIsShuttingDown(): Unit = {
    val processManager = SbtProcessManager.forProject(getMyProject)

    // Wait until sbt reached the blocking onLoad task -> the load is stuck and the shell is not ready.
    AwaitTestUtils.waitForConditionOrFail(DefaultCommandWaitTimeout, "sbt shell did not reach the blocking onLoad task") { () =>
      processListener.getLog.contains("LOAD_STARTED") && processManager.isAlive && !shellCommunication.currentState.isIdle
    }

    processManager.destroyProcess()

    AwaitTestUtils.waitForConditionOrFail(DefaultCommandWaitTimeout, "sbt did not print the loading failure prompt after the stop") { () =>
      processListener.getLog.contains(ProjectLoadingFailedPrompt)
    }

    AwaitTestUtils.waitForConditionOrFail(DefaultCommandWaitTimeout, "sbt shell did not stop after destroying the process") { () =>
      !processManager.isAlive
    }

    val events = shellCommunication.diagnosticEventsSnapshot
    val snapshot = shellCommunication.diagnosticsSnapshot

    // The shell was shutting down, so sendIgnore must have been skipped.
    assertDiagnosticEventExists[SbtShellDiagnosticEvent.SendIgnoreSkipped](events, snapshot)
    assertDiagnosticEventNotExists[SbtShellDiagnosticEvent.SendIgnore](events, snapshot)
  }
}

@Category(Array(classOf[SlowTests2]))
class SbtShellProjectLoadingFailedPromptIntegrationTest extends SbtShellProjectLoadingFailedPromptIntegrationTestBase

@Category(Array(classOf[SlowTests2]))
class SbtShellFailedReloadIntegrationTest_NewShellProjectLoading extends SbtShellProjectLoadingFailedPromptIntegrationTestBase {
  override protected def useNewShell: Boolean = true
}
