package org.jetbrains.sbt.process.mock

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.pom.java.LanguageLevel

import java.nio.file.{Files, Path}

/**
 * @see [[org.jetbrains.sbt.process.mock.MockSbtProcessForTests]]
 */
private[sbt] object MockSbtProcessForTestsSetup {

  private val DefaultMainClass = "MockSbtProcess"

  /**
   * The project JDK for tests that use the mock sbt process. The mock runs on the project JDK, like real sbt, and it's
   * compiled for Java 25: on Windows, it reads the console through the JDK's foreign function API (Java 22+).
   */
  val MockProcessJdk: LanguageLevel = LanguageLevel.JDK_25

  /**
   * On Windows, the mock calls native code through the JDK's foreign function API. Without this option, JDK 24+ prints
   * a warning on the first native call, and in a terminal it would end up in the sbt shell output.
   */
  private val MockProcessVmOptions: Seq[String] = Seq("--enable-native-access=ALL-UNNAMED")

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
      vmOptions = MockProcessVmOptions
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
