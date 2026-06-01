package org.jetbrains.sbt.process.mock

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

import java.nio.file.{Files, Path}

private[sbt] object MockSbtProcessForTestsSetup {

  private val DefaultMainClass = "MockSbtProcess"
  private val MockProcessClassesRelativePath: Path =
    Path.of("sbt/sbt-shell-runtime-tests/testdata/mockSbtProcess/classes")

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
  ): Unit = {
    assertUnitTestMode()

    val mockProcessClassesPath = defaultMockProcessClassesPath()

    val mockProcessData = new MockSbtProcessForTests.MockProcessData(
      mockProcessClassesPath,
      DefaultMainClass,
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

  private def defaultMockProcessClassesPath(): Path = {
    val workingDirectory = Path.of("").toAbsolutePath.normalize()
    val fromUltimateRoot = workingDirectory.resolve("community").resolve(MockProcessClassesRelativePath)
    val fromCommunityRoot = workingDirectory.resolve(MockProcessClassesRelativePath)

    val found = Seq(fromUltimateRoot, fromCommunityRoot).find(Files.isDirectory(_))
    found.getOrElse {
      throw new IllegalStateException(
        s"""Mock SBT process classes directory does not exist.
           |Expected tests to be started from either the ultimate root or the community root.
           |Checked:
           |  - ultimate root layout: $fromUltimateRoot
           |  - community root layout: $fromCommunityRoot""".stripMargin
      )
    }
  }
}
