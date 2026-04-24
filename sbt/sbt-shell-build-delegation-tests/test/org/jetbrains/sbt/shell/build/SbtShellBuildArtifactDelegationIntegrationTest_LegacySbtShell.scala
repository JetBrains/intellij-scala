package org.jetbrains.sbt.shell.build

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.RequiresJdk
import org.junit.Assert.assertFalse
import org.junit.experimental.categories.Category

import java.nio.charset.StandardCharsets
import java.nio.file.Files

@Category(Array(classOf[SlowTests2]))
@RequiresJdk(LanguageLevel.JDK_17)
class SbtShellBuildArtifactDelegationIntegrationTest_LegacySbtShell
  extends SbtShellBuildArtifactDelegationIntegrationTestBase {

  override protected def useNewSbtShell: Boolean = false

  def testBuildArtifact_sbt_1_11(): Unit = {
    runBuildArtifactDelegationTest(sbtVersion = SbtVersion.Latest.Sbt_1_11)
  }

  def testBuildArtifact_sbt_1_12(): Unit = {
    runBuildArtifactDelegationTest(sbtVersion = SbtVersion.Latest.Sbt_1_12)
  }

  def testBuildArtifact_sbt_2_0(): Unit = {
    runBuildArtifactDelegationTest(sbtVersion = SbtVersion.Latest.Sbt_2)
  }

  def testRebuildArtifact_sbt_1_11(): Unit = {
    val sbtVersion = SbtVersion.Latest.Sbt_1_11
    val scalaVersion = defaultScalaVersion

    prepareProjectForArtifactBuild(sbtVersion, scalaVersion)

    val artifactName = artifactNameForSbtVersion("delegated_rebuild_artifact", sbtVersion)
    val artifact = createRootMainModuleOutputJarArtifact(artifactName)

    val buildResult = fixture.rebuildArtifactsAndCaptureOutput(Seq(artifact))
    fixture.assertBuildSuccessful(buildResult)
    fixture.assertOutputMarkersForSbtVersion(buildResult, sbtVersion)

    assertRootMainClassFileExists(scalaVersion, sbtVersion)
    assertArtifactJarFileExists(artifactName)
  }

  def testBuildArtifactsBatch_sbt_1_11(): Unit = {
    val sbtVersion = SbtVersion.Latest.Sbt_1_11
    val scalaVersion = defaultScalaVersion

    prepareProjectForArtifactBuild(sbtVersion, scalaVersion)

    val artifactName1 = artifactNameForSbtVersion("delegated_batch_artifact_1", sbtVersion)
    val artifactName2 = artifactNameForSbtVersion("delegated_batch_artifact_2", sbtVersion)
    val artifact1 = createRootMainModuleOutputJarArtifact(artifactName1)
    val artifact2 = createRootMainModuleOutputJarArtifact(artifactName2)

    val buildResult = fixture.buildArtifactsAndCaptureOutput(Seq(artifact1, artifact2))
    fixture.assertBuildSuccessful(buildResult)
    fixture.assertOutputMarkersForSbtVersion(buildResult, sbtVersion)

    assertRootMainClassFileExists(scalaVersion, sbtVersion)
    assertArtifactJarFileExists(artifactName1)
    assertArtifactJarFileExists(artifactName2)
  }

  def testBuildArtifact_WhenSbtCompilationFails_SkipsPackaging_sbt_1_11(): Unit = {
    val sbtVersion = SbtVersion.Latest.Sbt_1_11
    prepareProjectForArtifactBuild(sbtVersion)

    val artifactName = artifactNameForSbtVersion("delegated_compile_failure_artifact", sbtVersion)
    val artifact = createRootMainModuleOutputJarArtifact(artifactName)

    val sourceFilePath = rootMainSourceFilePath
    val originalSource = Files.readString(sourceFilePath, StandardCharsets.UTF_8)
    val brokenSource =
      """object MainClass {
        |  def main(args: Array[String]): Unit = {
        |    val shouldFail: Int = \"boom\"
        |  }
        |}
        |""".stripMargin

    try {
      Files.writeString(sourceFilePath, brokenSource, StandardCharsets.UTF_8)

      val buildResult = fixture.buildArtifactsAndCaptureOutput(Seq(artifact))
      fixture.assertBuildFailed(buildResult)

      val artifactFilePath = artifactJarFilePath(artifactName)
      assertFalse(s"Artifact file '$artifactFilePath' should not exist after failed build", Files.exists(artifactFilePath))
    } finally {
      Files.writeString(sourceFilePath, originalSource, StandardCharsets.UTF_8)
    }
  }

  def testBuildArtifact_TestModuleOutput_UsesTestScopeCommand_sbt_1_11(): Unit = {
    val sbtVersion = SbtVersion.Latest.Sbt_1_11
    val scalaVersion = defaultScalaVersion

    prepareProjectForArtifactBuild(sbtVersion, scalaVersion)

    val artifactName = artifactNameForSbtVersion("delegated_test_output_artifact", sbtVersion)
    val artifact = createRootTestModuleOutputJarArtifact(artifactName)

    val buildResult = fixture.buildArtifactsAndCaptureOutput(Seq(artifact))
    fixture.assertBuildSuccessful(buildResult)
    fixture.assertOutputMarkersForSbtVersion(buildResult, sbtVersion)
    assertSbtBuildUsesTestScopeProducts(buildResult.sbtShellOutput)

    assertRootTestClassFileExists(scalaVersion, sbtVersion)
    assertArtifactJarFileExists(artifactName)
  }
}
