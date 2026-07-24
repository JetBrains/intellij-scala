package org.jetbrains.scalaCli.project

import com.intellij.execution.process.ProcessOutputType
import com.intellij.ide.projectWizard.NewProjectWizardConstants
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener, ExternalSystemTaskType}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.utils.ScalaInstallationTestUtils.*
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.NioFiles
import com.intellij.testFramework.FixtureRuleKt.useProject
import com.intellij.testFramework.{JUnit38AssumeSupportRunner, UsefulTestCase}
import com.intellij.util.system.CpuArch
import junitparams.naming.TestCaseName
import junitparams.{JUnitParamsRunner, Parameters}
import org.jetbrains.bsp.{BSP, BspProjectStructureImportingTestUtils}
import org.jetbrains.bsp.protocol.BspCommunicationService
import org.jetbrains.plugins.scala.LatestScalaVersions.*
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.project.utils.ScalaInstallationTestUtils
import org.jetbrains.sbt.project.ProjectStructureDsl.*
import org.jetbrains.sbt.project.template.wizard.buildSystem.BuildSystemScalaNewProjectWizardData.scalaBuildSystemData
import org.jetbrains.sbt.project.template.wizard.buildSystem.ScalaNewProjectWizardData.scalaData
import org.jetbrains.sbt.project.utils.ProjectComparisonOptions
import org.jetbrains.sbt.project.{NewScalaProjectWizardTestBase, ProjectStructureAssertionsFixture, ProjectStructureTestUtils}
import org.junit.{Assume, Test}
import org.junit.runner.RunWith

import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.annotation.unused
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.sys.process.{Process, ProcessLogger}
import scala.util.Try

// TODO:
//  - test .gitignore creation
//  - check added sample code
/**
 *  The Scala CLI tests are executed only on Linux or macOS machines; on other systems, they are ignored.
 *
 * It may happen that in the testing terminal something like this will appear:
 * `Caused by: java.lang.RuntimeException: BSP server not initialized yet`.
 * It's caused by "build/exit" request, which is called when closing all BSP sessions.
 * This issue also occurs in production if you click "close" on the BSP session quickly after the reload (this usually happens on the first reload).
 * However, it is not reported to the users. It seems to me as a bug on the server side.
 * The bsp server should be initialized - BSP sessions are closed after the reload,
 * so the endpoint to download all targets has already been triggered, and the response has been received.
 */
abstract class NewScalaCliProjectWizardTestBase extends NewScalaProjectWizardTestBase {

  /**
   * All tests in this class have the same project name.
   * This is just a simplification, so that in the #setUp method, the Scala CLI run script can be placed in the right place.
   */
  private val projectName = "scalaCliProjectName"

  override protected def setUp(): Unit = {
    ignoreTestIfSystemIsNotAllowed()
    super.setUp()
    // The BSP sessions need to be closed when the project resolver is finished,
    // otherwise the tests hang as they wait for all processes running on the application-pooled thread to close (see #SCL-23061).
    // Ideally, ProjectWizardTestCase.waitForConfiguration could be overridden to close some actions there,
    // but this cannot be done for BSP sessions because the project sync happens asynchronously,
    // and the session could be closed in the middle of the sync.
    val closeAllBspInstancesAfterReload = new ExternalSystemTaskNotificationListener {
      // Required override, otherwise the default implementation throws a StackOverflowError
      // because of mutual calling of two `onTaskOutput` methods.
      override def onTaskOutput(id: ExternalSystemTaskId, text: String, outputType: ProcessOutputType): Unit = {}

      override def onEnd(projectPath: String, id: ExternalSystemTaskId): Unit = {
        val isProjectResolveTask = id.getType == ExternalSystemTaskType.RESOLVE_PROJECT
        if (isProjectResolveTask) {
          BspCommunicationService.getInstance.closeAll
        }
      }
    }
    ExternalSystemTaskNotificationListener.EP_NAME.getPoint.registerExtension(closeAllBspInstancesAfterReload, getTestRootDisposable)
  }

  protected def runSimpleCreateProjectTest(
    scalaVersion: String,
    expectedSemanticDbVersion: String = "0.10.0",
    useIndentationBasedSyntax: Boolean = false,
    shouldExcludeScalaBuild: Boolean = true // Scala CLI bundled in Scala 3.5.2 & 3.6.7 doesn't have '.scala-build' in the output paths
  ): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk(useEnv = false)(scalaVersion, BSP.ProjectSystemId, useScalaSdkExtraClasspath = false)
    //noinspection TypeAnnotation
    val expectedProject = new project(projectName) {
      val projectLibraries = scalaLibraries :+ new library(s"BSP: semanticdb-javac-$expectedSemanticDbVersion")

      libraries := projectLibraries
      modules := Seq(
        new module(projectName) {
          libraryDependencies := BspProjectStructureImportingTestUtils.expectedLibraryDependencies(projectLibraries, projectName)
          sources := Seq("project.scala")
          testSources := Seq()
          resources := Seq()
          testResources := Seq()
          excluded := Seq(".bsp", ".bloop") ++ (if (shouldExcludeScalaBuild) Seq(".scala-build") else Nil)
        }
      )
    }

    runCreateScalaCliProjectTest(scalaVersion, expectedProject, useIndentationBasedSyntax)
  }

  private def runCreateScalaCliProjectTest(scalaVersion: String, expectedProject: project, useIndentationBasedSyntax: Boolean): Unit = {
    val project = createScalaProject(NewProjectWizardConstants.Language.SCALA, projectName, checkJDK = false) { step =>
      scalaBuildSystemData(step).setBuildSystem("Scala CLI")
      val data = scalaData(step)
      data.setScalaVersion(scalaVersion)
      data.setUseIndentationBasedSyntax(useIndentationBasedSyntax)
    }

    useProject(project, false, (project: Project) => {
      val assertions = new ProjectStructureAssertionsFixture(project)
      val compareContextNew = assertions.defaultCompareContext.withOptions(ProjectComparisonOptions(projectName))
      assertions.assertProjectsEqual(expectedProject)(using compareContextNew)
      junit.framework.TestCase.assertEquals(
        "The 'Use indentation-based syntax' setting was not configured correctly",
        useIndentationBasedSyntax,
        ScalaCodeStyleSettings.getInstance(project).USE_SCALA3_INDENTATION_BASED_SYNTAX
      )
    })
  }

  // The test framework's getContentRoot and scala.sys.process APIs only provide/accept java.io.File; there is no nio.Path-based alternative.
  //noinspection SSBasedInspection
  protected def installScalaCli(): Unit = {
    val projectDirectory = getContentRoot.toPath.resolve(projectName)
    //note: it's necessary to create this directory at this point, because naturally,
    // it is only created directly inside the test, but we already need this path to be able to add the Scala CLI script there.
    Files.createDirectories(projectDirectory)

    //note: only Linux and macOS systems are allowed
    val cpuArch = if (CpuArch.isArm64) "aarch64" else "x86_64"
    val os = if (SystemInfo.isLinux) "pc-linux-static" else "apple-darwin"
    val archiveName = s"scala-cli-$cpuArch-$os.gz"

    // TODO described in org.jetbrains.scalaCli.project.NewScalaCliProjectWizard_ScalaWithScalaCLI.scalaVersionsParameters
    //  (the URL for latest version https://github.com/Virtuslab/scala-cli/releases/latest/download/$archiveName)
    val curlCommand = s"curl --fail --location https://github.com/VirtusLab/scala-cli/releases/download/v1.5.4/$archiveName"
    val gzipCommand = "gzip --decompress"
    val curlProcess = Process(curlCommand) #| Process(gzipCommand, projectDirectory.toFile)

    val outputFile = projectDirectory.resolve("scala-cli").toFile

    val stderr = new StringBuilder
    val processChain = curlProcess #> outputFile #&& Process(s"chmod +x $outputFile")

    import scala.concurrent.ExecutionContext.Implicits.global
    val processFuture = Future {
      processChain! ProcessLogger(_ => (), stderr append _ + "\n")
    }

    val isSuccess = Try {
      val exitCode = Await.result(processFuture, Duration(2, TimeUnit.MINUTES))
      exitCode == 0
    }.getOrElse(false)

    if (!isSuccess) {
      throw new Exception(s"Cannot install Scala CLI \n $stderr")
    }
  }

  /**
   * Installs a specific Scala version to the test project directory.
   * Downloads and extracts the Scala distribution, then creates a wrapper script at `./scala`
   * that forwards to the actual Scala executable.
   *
   * @see [[createScalaWrapperScript]]
   */
  protected def installScala(scalaVersion: ScalaVersion): Unit = {
    // The test framework's getContentRoot returns java.io.File; there is no nio.Path-based alternative.
    //noinspection SSBasedInspection
    val testDirectory = getContentRoot.toPath.resolve(projectName)
    Files.createDirectories(testDirectory)

    val zipFile = ScalaInstallationTestUtils.downloadScalaDistribution(scalaVersion, testDirectory)
    unzipScalaSdkArchive(zipFile, testDirectory, customRootDir = Some("scala-root"))

    createScalaWrapperScript(testDirectory)
  }

  /**
   * Creates a wrapper script that forwards to the actual Scala executable.
   * This allows the use of the `./scala` command in test project directories.
   */
  private def createScalaWrapperScript(testDirectory: Path): Unit = {
    val scalaDir = testDirectory.resolve("scala-root")

    // Create wrapper script
    val scriptContent =
      s"""#!/bin/bash
         |exec "${scalaDir.resolve("bin/scala").toAbsolutePath}" "$$@"
         |""".stripMargin
    val wrapperScript = testDirectory.resolve("./scala")
    Files.write(wrapperScript, scriptContent.getBytes)
    NioFiles.setExecutable(wrapperScript)

    // Make the required executables in bin directory executable
    val executablePaths = Seq("bin/scala", "bin/scalac", "bin/scala-cli", "libexec/scala-cli")
    executablePaths.foreach { dir =>
      val executablePath = scalaDir.resolve(dir)
      if (Files.exists(executablePath) && Files.isRegularFile(executablePath))
        NioFiles.setExecutable(executablePath)
    }
  }

  /**
   * Ignores test if the operating system is not Linux or Mac
   */
  private def ignoreTestIfSystemIsNotAllowed(): Unit = {
    val isAllowed = SystemInfo.isLinux || SystemInfo.isMac
    Assume.assumeTrue("The operating system is not allowed (Linux/macOS)", isAllowed)
  }
}

/**
 * Tests with standalone Scala CLI installed.
 */
@RunWith(classOf[JUnit38AssumeSupportRunner])
class NewScalaCliProjectWizardTest_ScalaCli extends NewScalaCliProjectWizardTestBase {

  override protected def setUp(): Unit = {
    super.setUp()
    installScalaCli()
  }

  def testCreateSimpleProjectScala2(): Unit =
    runSimpleCreateProjectTest("2.13.14", shouldExcludeScalaBuild = false)

  def testCreateSimpleProjectScala3(): Unit =
    runSimpleCreateProjectTest("3.0.2", shouldExcludeScalaBuild = false)

  def testCreateSimpleProjectScala3AndUseIndentationBasedSyntax(): Unit =
    runSimpleCreateProjectTest( "3.3.3", useIndentationBasedSyntax = true, shouldExcludeScalaBuild = false)
}

/**
 * Tests with installed Scala versions that do not have Scala CLI bundled - expects failure.
 * The tests cover Scala versions 2.10-2.13 and Scala 3.0-3.4.
 */
@RunWith(classOf[JUnitParamsRunner])
class NewScalaCliProjectWizard_ScalaWithoutScalaCLI extends NewScalaCliProjectWizardTestBase {

  @unused
  private def scalaVersionsParameters: Array[AnyRef] = {
    val all = LatestScalaVersions.allStableWithoutScalaNext :+ Scala_3_4
    // Scala 2.9 zip is not available on GitHub, so for simplicity just ignored
    all.filterNot(_ == Scala_2_9).toArray
  }

  @Test
  @Parameters(method = "scalaVersionsParameters")
  @TestCaseName("{method}[{0}]")
  def testCreateProjectUsingScalaWithoutScalaCLI(scalaVersion: ScalaVersion): Unit = {
    installScala(scalaVersion)

    // Scala 3.0.2 reports a slightly different version output, which is not directly parsed
    // in org.jetbrains.scalaCli.ScalaCliUtils.parseScalaVersion. It doesn't matter much in production.
    val expectedErrorMessage =
      if (scalaVersion == Scala_3_0) "Unable to parse Scala version from output: Scala compiler version 3.0.2"
      else s"Scala version ${scalaVersion.minor} is lower than 3.5.0"

    UsefulTestCase.assertThrows(
      classOf[Exception],
      expectedErrorMessage,
      () => runSimpleCreateProjectTest("3.0.2")
    )
  }
}

/**
 * Tests with installed Scala versions that have Scala CLI bundled.
 * The tests cover Scala versions 3.5-3.8.
 */
@RunWith(classOf[JUnitParamsRunner])
class NewScalaCliProjectWizard_ScalaWithScalaCLI extends NewScalaCliProjectWizardTestBase {

  @unused
  private def scalaVersionsParameters: Array[AnyRef] = {
    // TODO
    //  Remove the Scala 3.8 & 3.7 version from ignored list and update the Scala CLI standalone installation method
    //  to use the latest version once the issue with downloading artifacts from the Sonatype snapshots repo is fixed in Scala CLI.
    //  The Sonatype snapshots repo used in Scala CLI behaves unpredictably in some cases.
    //  For certain dependencies, requests may hang indefinitely, preventing the BSP server from starting and causing test failures.
    //  This issue occurs with several of the latest Scala CLI versions (including 1.12.1 used with Scala 3.8.1), where it hangs while downloading bloop artifacts.
    //  Scala 3.7 is included in the ignored tests because it takes ~6 minutes to run on CI (also due to downloading Sonatype dependencies).
    val ignored = Seq(Scala_3_4, Scala_3_8, Scala_3_7)
    LatestScalaVersions.allScalaNext.diff(ignored).toArray
  }

  @Test
  @Parameters(method = "scalaVersionsParameters")
  @TestCaseName("{method}[{0}]")
  def testCreateProjectUsingScalaWithScalaCLI(scalaVersion: ScalaVersion): Unit = {
    installScala(scalaVersion)

    val shouldExcludeScalaBuild =
      if (scalaVersion == Scala_3_5 || scalaVersion == Scala_3_6) false
      else true

    val expectedSemanticDbVersion = scalaVersion match {
      case ScalaVersion.Latest.Scala_3_9 => "0.12.3"
      case _ => "0.10.0"
    }

    runSimpleCreateProjectTest("3.0.2", expectedSemanticDbVersion = expectedSemanticDbVersion, useIndentationBasedSyntax = false, shouldExcludeScalaBuild = shouldExcludeScalaBuild)
  }
}
