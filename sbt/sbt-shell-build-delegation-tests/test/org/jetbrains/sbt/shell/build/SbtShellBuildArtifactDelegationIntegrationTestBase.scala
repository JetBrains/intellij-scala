package org.jetbrains.sbt.shell.build

import com.intellij.compiler.artifacts.ArtifactsTestUtil
import com.intellij.openapi.module.Module
import com.intellij.packaging.artifacts.{Artifact, ArtifactManager}
import com.intellij.packaging.elements.PackagingElementFactory
import com.intellij.packaging.impl.artifacts.JarArtifactType
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.{PathExt, inWriteAction}
import org.jetbrains.sbt.{SbtTestDataUtils, SbtVersion}
import org.junit.Assert.assertTrue

import java.nio.file.{Files, Path}

/**
 * Integration tests for delegated sbt-shell artifact builds.
 */
abstract class SbtShellBuildArtifactDelegationIntegrationTestBase extends SbtShellBuildDelegationTestBase {

  private val sbtRootProjectDirName = "simpleProjectForBuildDelegationTest"

  override protected def getTestDataProjectPath: String =
    SbtTestDataUtils.resolveRelativePath(
      s"sbt-shell-build-delegation-tests/testdata/projects/$sbtRootProjectDirName",
    )

  protected final def defaultScalaVersion: ScalaVersion =
    ScalaVersion.fromString("3.8.3").get

  protected final def prepareProjectForArtifactBuild(
    sbtVersion: SbtVersion,
    scalaVersion: ScalaVersion = defaultScalaVersion,
  ): Unit = {
    fixture.prepareProjectAndImport(sbtVersion, scalaVersion)
    fixture.injectInvalidJpsScalacOption(findRootMainModule())
  }

  protected final def createRootMainModuleOutputJarArtifact(artifactName: String): Artifact =
    createModuleOutputJarArtifact(artifactName, findRootMainModule(), isTestOutput = false)

  protected final def createRootTestModuleOutputJarArtifact(artifactName: String): Artifact =
    createModuleOutputJarArtifact(artifactName, findRootTestModule(), isTestOutput = true)

  protected final def assertRootMainClassFileExists(
    scalaVersion: ScalaVersion,
    sbtVersion: SbtVersion
  ): Unit =
    assertClassFileExists(
      scalaVersion = scalaVersion.minor,
      sbtVersion = sbtVersion,
      fileName = "MainClass.class",
      isTest = false,
    )

  protected final def assertRootTestClassFileExists(
    scalaVersion: ScalaVersion,
    sbtVersion: SbtVersion
  ): Unit =
    assertClassFileExists(
      scalaVersion = scalaVersion.minor,
      sbtVersion = sbtVersion,
      fileName = "TestClass.class",
      isTest = true,
    )

  protected final def assertArtifactJarFileExists(artifactName: String): Unit = {
    val artifactFilePath = artifactJarFilePath(artifactName)
    assertTrue(s"Artifact file '$artifactFilePath' does not exist", Files.exists(artifactFilePath))
  }

  protected final def artifactJarFilePath(artifactName: String): Path =
    getTestProjectPath / "out" / "artifacts" / s"$artifactName.jar"

  protected final def rootMainSourceFilePath: Path =
    getTestProjectPath / "src" / "main" / "scala" / "MainClass.scala"

  protected final def assertSbtBuildUsesTestScopeProducts(buildOutput: String): Unit = {
    assertTrue(
      s"Sbt shell output should contain '/Test/products'. Output:\n$buildOutput",
      buildOutput.contains("/Test/products")
    )
  }

  protected final def artifactNameForSbtVersion(prefix: String, sbtVersion: SbtVersion): String = {
    val sbtVersionId = sbtVersion.minor.replaceAll("[^0-9A-Za-z]+", "_")
    s"${prefix}_${sbtVersionId}"
  }

  protected def runBuildArtifactDelegationTest(sbtVersion: SbtVersion): Unit =
    runBuildArtifactDelegationTest(sbtVersion, defaultScalaVersion)

  private def runBuildArtifactDelegationTest(
    sbtVersion: SbtVersion,
    scalaVersion: ScalaVersion
  ): Unit = {
    prepareProjectForArtifactBuild(sbtVersion, scalaVersion)

    val artifactName = artifactNameForSbtVersion("delegated_artifact", sbtVersion)
    val artifact = createRootMainModuleOutputJarArtifact(artifactName)

    val buildResult = fixture.buildArtifactsAndCaptureOutput(Seq(artifact))
    fixture.assertBuildSuccessful(buildResult)
    fixture.assertOutputMarkersForSbtVersion(buildResult, sbtVersion)

    assertRootMainClassFileExists(scalaVersion, sbtVersion)
    assertArtifactJarFileExists(artifactName)
  }

  private def createModuleOutputJarArtifact(
    artifactName: String,
    module: Module,
    isTestOutput: Boolean,
  ): Artifact = {
    val artifactOutputPath = (getTestProjectPath / "out" / "artifacts").toString

    inWriteAction {
      val artifactManager = ArtifactManager.getInstance(getMyProject)
      val artifactModel = artifactManager.createModifiableModel()
      val artifact = artifactModel.addArtifact(artifactName, JarArtifactType.getInstance())

      val moduleOutput =
        if (isTestOutput) PackagingElementFactory.getInstance().createTestModuleOutput(module)
        else PackagingElementFactory.getInstance().createModuleOutput(module)
      artifact.getRootElement.addOrFindChild(moduleOutput)

      artifactModel.getOrCreateModifiableArtifact(artifact).setOutputPath(artifactOutputPath)
      artifactModel.commit()
    }

    ArtifactsTestUtil.findArtifact(getMyProject, artifactName)
  }

  private def assertClassFileExists(
    scalaVersion: String,
    sbtVersion: SbtVersion,
    fileName: String,
    isTest: Boolean,
  ): Unit = {
    val relativePath =
      if (sbtVersion.isSbt2) {
        val outputProjectDirName = sbt2OutputProjectDirName(sbtRootProjectName)
        if (isTest) s"target/out/jvm/scala-$scalaVersion/$outputProjectDirName/test-classes/$fileName"
        else s"target/out/jvm/scala-$scalaVersion/$outputProjectDirName/classes/$fileName"
      }
      else
        if (isTest) s"target/scala-$scalaVersion/test-classes/$fileName"
        else s"target/scala-$scalaVersion/classes/$fileName"

    val filePath = getTestProjectPath / relativePath
    assertTrue(s"Compiled class file '$filePath' does not exist", Files.exists(filePath))
  }
}
