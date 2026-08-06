package org.jetbrains.sbt.process.mock

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

import java.nio.file.{Files, Path}

/**
 * @see [[org.jetbrains.sbt.process.mock.MockSbtProcessForTests]]
 */
private[sbt] object MockSbtProcessForTestsSetup {

  private val DefaultMainClass = "MockSbtProcess"

  /**
   * Substitutes the lightweight `MockSbtProcess` JVM for the real sbt launcher.
   *
   * This is purely about *enablement* of the mock and is independent of the sbt shell flavor. The flavor is resolved
   * later, at process-launch time, from production settings the test configures separately — the run configuration's
   * `useSbtShell` flag and the `sbt.new.shell` registry key (see
   * [[org.jetbrains.sbt.shell.SbtShellTestUtil.setNewSbtShellEnabled]]).
   */
  def enableMockSbtProcess(
    project: Project,
    parentDisposable: Disposable,
    slowShutdownReleaseFile: Option[Path] = None,
    slowShutdownStartedFile: Option[Path] = None,
  ): Unit = {
    assertUnitTestMode()

    val classesPath = mockProcessClassesPath()

    val mockProcessData = new MockSbtProcessForTests.MockProcessData(
      classesPath,
      DefaultMainClass,
      slowShutdownReleaseFile,
      slowShutdownStartedFile,
    )
    project.putUserData(MockSbtProcessForTests.MockProcessDataProjectStateKey, mockProcessData)
    Disposer.register(parentDisposable, () => project.putUserData(MockSbtProcessForTests.MockProcessDataProjectStateKey, null))
  }

  private def assertUnitTestMode(): Unit = {
    val application = ApplicationManager.getApplication
    assert(
      application != null && application.isUnitTestMode,
      "Mock SBT process can only be enabled in unit test mode"
    )
  }

  private def mockProcessClassesPath(): Path = {
    val path = Path.of(MockSbtProcessBuildInfo.testClassesDirectory)
    if (Files.isDirectory(path)) {
      path
    } else {
      throw new IllegalStateException(
        s"""Mock SBT process classes directory does not exist.
           |Expected the sbt-mock-process Test configuration to be compiled before sbt-shell-runtime-tests.
           |Checked: $path""".stripMargin
      )
    }
  }
}
