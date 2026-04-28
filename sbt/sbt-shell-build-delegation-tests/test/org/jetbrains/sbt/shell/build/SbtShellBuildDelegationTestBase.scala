package org.jetbrains.sbt.shell.build

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.jetbrains.sbt.shell.SbtShellTestUtil
import org.junit.Assert.assertNotNull

import java.util.Locale

/**
 * Common infrastructure for sbt-shell build delegation integration tests.
 */
abstract class SbtShellBuildDelegationTestBase extends SbtExternalSystemImportingTestLike {

  protected def useNewSbtShell: Boolean

  protected final val sbtRootProjectName = "simpleProjectForBuildDelegationTest"

  private lazy val buildTestFixture = new SbtShellBuildTestFixture(
    testName = getClass.getSimpleName,
    project = getMyProject,
    testProjectPath = getTestProjectPath,
    importProject = () => importProject(false),
  )

  protected final def fixture: SbtShellBuildTestFixture =
    buildTestFixture

  override protected def copyTestProjectToTemporaryDir: Boolean = true

  // Running on EDT would lead to a deadlock as some logic inside SbtBuildCommandsFactory requires EDT
  override def runInDispatchThread(): Boolean = false

  override def setUp(): Unit = {
    super.setUp()
    getCurrentExternalProjectSettings.useSbtShellForBuild = true
    SbtShellTestUtil.setNewSbtShellEnabled(useNewSbtShell, getTestRootDisposable)
  }

  override def tearDown(): Unit = {
    // TODO SCL-12039: compile server should not be started for delegated sbt-shell builds
    CompileServerLauncher.stopServerAndWait()
    super.tearDown()
  }

  /**
   * sbt 2.x stores delegated compile outputs under a lower-cased project-name directory segment
   * (for example: target/out/jvm/scala-3.x/simpleproject/...).
   *
   * The same project can still be referred to with mixed case in module IDs and sbt commands,
   * so assertions for sbt2 output paths must normalize the project directory name explicitly.
   * This avoids false negatives on TeamCity Linux where path case matters.
   */
  protected final def sbt2OutputProjectDirName(projectName: String): String =
    projectName.toLowerCase(Locale.ROOT)

  protected final def findRootMainModule(): Module = {
    val module = getModule(s"$sbtRootProjectName.main")
    assertNotNull(s"Could not find module '$sbtRootProjectName.main'", module)
    module
  }

  protected final def findRootTestModule(): Module = {
    val module = getModule(s"$sbtRootProjectName.test")
    assertNotNull(s"Could not find module '$sbtRootProjectName.test'", module)
    module
  }
}
