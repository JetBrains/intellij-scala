package org.jetbrains.sbt.shell.build

import com.intellij.compiler.artifacts.ArtifactsTestUtil
import com.intellij.openapi.module.Module
import com.intellij.packaging.artifacts.{Artifact, ArtifactManager}
import com.intellij.packaging.elements.PackagingElementFactory
import com.intellij.packaging.impl.artifacts.JarArtifactType
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.extensions.{PathExt, inWriteAction}
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.jetbrains.sbt.shell.SbtShellTestUtil
import org.junit.Assert.{assertNotNull, assertTrue}

import java.nio.file.{Files, Path}

/**
 * Integration tests for delegated sbt-shell artifact builds.
 */
abstract class SbtShellBuildArtifactDelegationIntegrationTestBase extends SbtExternalSystemImportingTestLike {

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

  // Running on EDT would lead to a deadlock as some logic inside SbtBuildCommandsFactory requires EDT
  override def runInDispatchThread() = false

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

  protected final def defaultScalaVersion: ScalaVersion =
    ScalaVersion.fromString("3.8.3").get

  protected final def fixture: SbtShellBuildTestFixture =
    buildTestFixture

  protected final def prepareProjectForArtifactBuild(
    sbtVersion: SbtVersion,
    scalaVersion: ScalaVersion = defaultScalaVersion,
  ): Unit = {
    buildTestFixture.prepareProjectAndImport(sbtVersion, scalaVersion)
    buildTestFixture.injectInvalidJpsScalacOption(findRootMainModule())
  }

  protected final def createRootMainModuleOutputJarArtifact(artifactName: String): Artifact =
    createModuleOutputJarArtifact(artifactName, findRootMainModule())

  protected final def createRootTestModuleOutputJarArtifact(artifactName: String): Artifact =
    createModuleOutputJarArtifact(artifactName, findRootTestModule())

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
  ): Artifact = {
    val artifactOutputPath = (getTestProjectPath / "out" / "artifacts").toString

    inWriteAction {
      val artifactManager = ArtifactManager.getInstance(getMyProject)
      val artifactModel = artifactManager.createModifiableModel()
      val artifact = artifactModel.addArtifact(artifactName, JarArtifactType.getInstance())

      val moduleOutput = PackagingElementFactory.getInstance().createModuleOutput(module)
      artifact.getRootElement.addOrFindChild(moduleOutput)

      artifactModel.getOrCreateModifiableArtifact(artifact).setOutputPath(artifactOutputPath)
      artifactModel.commit()
    }

    ArtifactsTestUtil.findArtifact(getMyProject, artifactName)
  }

  private def findRootMainModule(): Module = {
    val module = getModule(s"$sbtRootProjectName.main")
    assertNotNull(s"Could not find module '$sbtRootProjectName.main'", module)
    module
  }

  private def findRootTestModule(): Module = {
    val module = getModule(s"$sbtRootProjectName.test")
    assertNotNull(s"Could not find module '$sbtRootProjectName.test'", module)
    module
  }

  private def assertClassFileExists(
    scalaVersion: String,
    sbtVersion: SbtVersion,
    fileName: String,
    isTest: Boolean,
  ): Unit = {
    val relativePath =
      if (sbtVersion.isSbt2)
        if (isTest) s"target/out/jvm/scala-$scalaVersion/$sbtRootProjectName/test-classes/$fileName"
        else s"target/out/jvm/scala-$scalaVersion/$sbtRootProjectName/classes/$fileName"
      else
        if (isTest) s"target/scala-$scalaVersion/test-classes/$fileName"
        else s"target/scala-$scalaVersion/classes/$fileName"

    val filePath = getTestProjectPath / relativePath
    assertTrue(s"Compiled class file '$filePath' does not exist", Files.exists(filePath))
  }
}
