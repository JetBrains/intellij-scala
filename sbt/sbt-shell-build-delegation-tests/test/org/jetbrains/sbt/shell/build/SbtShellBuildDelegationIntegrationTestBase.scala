package org.jetbrains.sbt.shell.build

import com.intellij.task.{ProjectTaskContext, ProjectTaskManager}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.sbt.shell.SbtProjectTaskRunnerImpl
import org.jetbrains.sbt.{SbtTestDataUtils, SbtVersion}
import org.junit.Assert.{assertNotNull, assertTrue}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.IteratorHasAsScala

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
      // In sbt 2 the layout of the target dir has completely changed.
      val outputProjectDirName = sbt2OutputProjectDirName(sbtRootProjectName)
      assertFileExists(s"target/out/jvm/scala-$scalaVersionStr/$outputProjectDirName/classes/MainClass.class")
      assertFileExists(s"target/out/jvm/scala-$scalaVersionStr/$outputProjectDirName/test-classes/TestClass.class")
    } else {
      assertFileExists(s"target/scala-$scalaVersionStr/classes/MainClass.class")
      assertFileExists(s"target/scala-$scalaVersionStr/test-classes/TestClass.class")
    }
  }

  def testBuildDefinitionModuleTaskIsClaimedAndIgnored(): Unit = {
    val sbtVersion = SbtVersion.Latest.Sbt_1_11
    val scalaVersion = ScalaVersion.fromString("3.8.3").get

    fixture.prepareProjectAndImport(sbtVersion, scalaVersion)

    val buildDefinitionModule = findRootBuildDefinitionModule()
    assertNotNull("Could not find sbt build-definition module", buildDefinitionModule)

    val buildTask = ProjectTaskManager.getInstance(getMyProject).createModulesBuildTask(buildDefinitionModule, true, true, false)

    val canRunInSbtRunner = new SbtProjectTaskRunnerImpl().canRun(getMyProject, buildTask, new ProjectTaskContext())
    assertTrue(
      "Sbt runner must claim sbt build-definition module tasks when delegated builds are enabled to prevent fallback to JPS",
      canRunInSbtRunner
    )

    val buildResult = fixture.buildModulesAndCaptureOutput(buildTask)
    fixture.assertBuildSuccessful(buildResult)
    fixture.assertCompileServerIsNotRunning()
    assertNoClassFilesInBuildOutputDirectories()
  }

  private def assertFileExists(relativeFilePath: String): Unit = {
    val filePath = getTestProjectPath / relativeFilePath
    assertTrue(s"File '$filePath' does not exist", Files.exists(filePath))
  }

  private def assertNoClassFilesInBuildOutputDirectories(): Unit = {
    val classFiles = collectClassFilesFromTargetDir()
    assertTrue(
      s"Build-definition module task should not generate any .class files in target directory, but found:\n${classFiles.mkString("\n")}",
      classFiles.isEmpty
    )
  }

  private def collectClassFilesFromTargetDir(): Seq[Path] = {
    val targetDir = getTestProjectPath / "target"
    Files.walk(targetDir).iterator().asScala.filter(_.toString.endsWith(".class")).toSeq
  }
}
