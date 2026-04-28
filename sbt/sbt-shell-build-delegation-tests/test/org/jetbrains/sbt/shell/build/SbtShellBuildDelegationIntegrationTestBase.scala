package org.jetbrains.sbt.shell.build

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.sbt.{SbtTestDataUtils, SbtVersion}
import org.junit.Assert.assertTrue

import java.nio.file.Files

/**
 * Integration tests for delegated sbt-shell builds
 */
abstract class SbtShellBuildDelegationIntegrationTestBase extends SbtShellBuildDelegationTestBase {

  private val sbtRootProjectDirName = "simpleProjectForBuildDelegationTest"

  override protected def getTestDataProjectPath: String =
    SbtTestDataUtils.resolveRelativePath(
      s"sbt-shell-build-delegation-tests/testdata/projects/$sbtRootProjectDirName",
    )

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
    fixture.prepareProjectAndImport(sbtVersion, scalaVersion)

    fixture.injectInvalidJpsScalacOption(findRootMainModule())

    val buildResult = fixture.buildAllModulesAndCaptureOutput()
    fixture.assertBuildSuccessful(buildResult)
    fixture.assertOutputMarkersForSbtVersion(buildResult, sbtVersion)

    val scalaVersionStr = scalaVersion.minor
    if (sbtVersion.isSbt2) {
      // In sbt the layout of the target dir has completely changed
      val outputProjectDirName = sbt2OutputProjectDirName(sbtRootProjectName)
      assertFileExists(s"target/out/jvm/scala-$scalaVersionStr/$outputProjectDirName/classes/MainClass.class")
      assertFileExists(s"target/out/jvm/scala-$scalaVersionStr/$outputProjectDirName/test-classes/TestClass.class")
    } else {
      assertFileExists(s"target/scala-$scalaVersionStr/classes/MainClass.class")
      assertFileExists(s"target/scala-$scalaVersionStr/test-classes/TestClass.class")
    }

    //TODO: assert that compile server is not running when SCL-12039 is fixed
  }

  private def assertFileExists(relativeFilePath: String): Unit = {
    val filePath = getTestProjectPath / relativeFilePath
    assertTrue(s"File '$filePath' does not exist", Files.exists(filePath))
  }
}
