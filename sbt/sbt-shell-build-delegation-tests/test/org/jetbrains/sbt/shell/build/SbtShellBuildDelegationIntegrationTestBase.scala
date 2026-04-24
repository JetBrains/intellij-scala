package org.jetbrains.sbt.shell.build

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.jetbrains.sbt.shell.SbtShellTestUtil
import org.junit.Assert.{assertNotNull, assertTrue}

import java.nio.file.Files

/**
 * Integration tests for delegated sbt-shell builds
 */
abstract class SbtShellBuildDelegationIntegrationTestBase extends SbtExternalSystemImportingTestLike {

  protected def useNewSbtShell: Boolean

  private val sbtRootProjectDirName = "simpleProjectForBuildDelegationTest"
  private val sbtRootProjectName = "simpleProjectForBuildDelegationTest"
  private lazy val buildTestFixture = new SbtShellBuildTestFixture(
    testName = getClass.getSimpleName,
    project = getMyProject,
    testProjectPath = getTestProjectPath,
    importProject = () => importProject(false),
  )

  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/$sbtRootProjectDirName"

  override protected def copyTestProjectToTemporaryDir: Boolean = true

  // Running on EDT would lead to a deadlock as some logic inside SbtBuildCommandPlanner requires EDT
  override def runInDispatchThread() = false

  override def setUp(): Unit = {
    super.setUp()

    getCurrentExternalProjectSettings.useSbtShellForBuild = true
    SbtShellTestUtil.setNewSbtShellEnabled(useNewSbtShell, getTestRootDisposable)
  }

  override def tearDown(): Unit = {
    //TODO: currently the compile server is started even if sbt shell is used for compilation
    // This seems like a bug. The CS should not be started unless other features need it (e.g. Worksheet or CBH)
    // For now we have to manually stop it here to avoid "Thread leaked" exceptions in tests
    //
    // TODO: fix SCL-12039 ad ensure tests
    CompileServerLauncher.stopServerAndWait()

    super.tearDown()
  }

  def testDelegateBuild_sbt_1_11(): Unit = {
    runDelegatedBuildTest(sbtVersion = SbtVersion.Latest.Sbt_1_11)
  }

  def testDelegateBuild_sbt_1_12(): Unit = {
    runDelegatedBuildTest(sbtVersion = SbtVersion.Latest.Sbt_1_12)
  }

  def testDelegateBuild_sbt_2_0(): Unit = {
    runDelegatedBuildTest(sbtVersion = SbtVersion.Latest.Sbt_2)
  }

  private def runDelegatedBuildTest(sbtVersion: SbtVersion): Unit =
    runDelegatedBuildTest(sbtVersion, scalaVersion = ScalaVersion.fromString("3.8.3").get)

  private def runDelegatedBuildTest(
    sbtVersion: SbtVersion,
    scalaVersion: ScalaVersion
  ): Unit = {
    buildTestFixture.prepareProjectAndImport(sbtVersion, scalaVersion)

    buildTestFixture.injectInvalidJpsScalacOption(findRootMainModule())

    val buildResult = buildTestFixture.buildAllModulesAndCaptureOutput()
    buildTestFixture.assertBuildSuccessful(buildResult)
    buildTestFixture.assertOutputMarkersForSbtVersion(buildResult, sbtVersion)

    val scalaVersionStr = scalaVersion.minor
    if (sbtVersion.isSbt2) {
      // In sbt the layout of the target dir has completely changed
      assertFileExists(s"target/out/jvm/scala-$scalaVersionStr/$sbtRootProjectName/classes/MainClass.class")
      assertFileExists(s"target/out/jvm/scala-$scalaVersionStr/$sbtRootProjectName/test-classes/TestClass.class")
    } else {
      assertFileExists(s"target/scala-$scalaVersionStr/classes/MainClass.class")
      assertFileExists(s"target/scala-$scalaVersionStr/test-classes/TestClass.class")
    }

    //TODO: assert that compile server is not running when SCL-12039 is fixed
  }

  private def findRootMainModule(): Module = {
    val module = getModule(s"$sbtRootProjectName.main")
    assertNotNull(s"Could not find module '${sbtRootProjectName}.main'", module)
    module
  }

  private def assertFileExists(relativeFilePath: String): Unit = {
    val filePath = getTestProjectPath / relativeFilePath
    assertTrue(s"File '$filePath' does not exist", Files.exists(filePath))
  }
}
