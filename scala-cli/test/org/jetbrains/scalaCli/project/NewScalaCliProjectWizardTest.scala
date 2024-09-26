package org.jetbrains.scalaCli.project

import com.intellij.ide.projectWizard.NewProjectWizardConstants
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.FixtureRuleKt.useProject
import com.intellij.testFramework.JUnit38AssumeSupportRunner
import org.jetbrains.bsp.BSP
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.sbt.project.{ExactMatch, NewScalaProjectWizardTestBase, ProjectStructureTestUtils}
import org.jetbrains.sbt.project.ProjectStructureDsl._
import org.jetbrains.sbt.project.ProjectStructureMatcher.ProjectComparisonOptions
import org.jetbrains.sbt.project.template.wizard.buildSystem.BuildSystemScalaNewProjectWizardData.scalaBuildSystemData
import org.jetbrains.sbt.project.template.wizard.buildSystem.ScalaNewProjectWizardData.scalaData
import org.jetbrains.scalaCli.ScalaCliUtils.TryIntOps
import org.junit.Assume
import org.junit.runner.RunWith

import java.nio.file.Files
import scala.sys.process.{Process, ProcessLogger}
import scala.util.Try


// TODO:
//  - test .gitignore creation
//  - check added sample code
/**
 *  The Scala CLI tests are only performed if they are run on a Linux machine.
 *  If they are run on any other operating system, they will be ignored.
 */
@RunWith(classOf[JUnit38AssumeSupportRunner])
class NewScalaCliProjectWizardTest extends NewScalaProjectWizardTestBase with ExactMatch {

  /**
   * All tests in this class have the same project name.
   * This is just a simplification, so that in the #setUp method, the Scala CLI run script can be placed in the right place.
   */
  private val projectName = "scalaCliProjectName"

  override protected def setUp(): Unit = {
    //note: ignores tests if the operating system is not Linux
    Assume.assumeTrue("The operating system is not Linux", SystemInfo.isLinux)
    super.setUp()
    installScalaCli()
  }

  override def tearDown(): Unit = {
    inWriteAction {
      val projectJdkTable = ProjectJdkTable.getInstance()
      projectJdkTable.getAllJdks.foreach(projectJdkTable.removeJdk)
    }
    super.tearDown()
  }

  def testCreateSimpleProjectScala2(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk(useEnv = false)("2.13.6", BSP.ProjectSystemId)
    runSimpleCreateSbtProjectTest("2.13.6", scalaLibraries)
  }

  //TODO #SCL-23031
//  def testCreateProjectWithDotsSpacesAndDashesInNameName(): Unit =
//    runSimpleCreateSbtProjectTest("project_name_with_dots spaces and-dashes and UPPERCASE")

  def testCreateSimpleProjectScala3(): Unit = {
    val scalaVersion = "3.0.2"
    val scalaLibraries =
      ProjectStructureTestUtils.expectedScalaLibrary(useEnv = false)("2.13.6", BSP.ProjectSystemId) +: ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk(useEnv = false)(scalaVersion,  BSP.ProjectSystemId)
    runSimpleCreateSbtProjectTest(scalaVersion, scalaLibraries)
  }

  private def runSimpleCreateSbtProjectTest(
    scalaVersion: String,
    scalaLibraries: Seq[library]
  ): Unit = {
    //noinspection TypeAnnotation
    val expectedProject = new project(projectName) {
      val projectLibraries = scalaLibraries :+ new library(s"BSP: semanticdb-javac-0.10.0")

      libraries := projectLibraries
      modules := Seq(
        new module(projectName) {
          //note: in the Scala CLI project in the root (and the only) module most dependencies appear twice - one with TEST and one with the Compile SCOPE.
          val allLibrariesWithoutScalaSDK = projectLibraries.filter(_.get(scalaSdkSettings).flatten.isEmpty)
          val testLibraries = allLibrariesWithoutScalaSDK :+ new library(s"BSP: $projectName test dependencies")
          // TODO test library dependencies scopes
          val allLibraryDependencies = projectLibraries ++ testLibraries
          libraryDependencies := allLibraryDependencies
          sources := Seq("project.scala")
          testSources := Seq()
          resources := Seq()
          testResources := Seq()
          excluded := Seq(".bsp", ".bloop")
        }
      )
    }

    runCreateScalaCliProjectTest(scalaVersion, expectedProject)
  }

  private def runCreateScalaCliProjectTest(scalaVersion: String, expectedProject: project): Unit = {
    val project = createScalaProject(NewProjectWizardConstants.Language.SCALA, projectName) { step =>
      scalaBuildSystemData(step).setBuildSystem("Scala CLI")
      scalaData(step).setScalaVersion(scalaVersion)
    }

    implicit val comparisonOptions: ProjectComparisonOptions = ProjectComparisonOptions(projectName)
    useProject(project, false, assertProjectsEqual(expectedProject, _: Project))
  }

  private def installScalaCli(): Unit = {
    val projectDirectory = getContentRoot.toPath.resolve(projectName)
    //note: it's necessary to create this directory at this point, because naturally,
    // it is only created directly inside the test, but we already need this path to be able to add the Scala CLI script there.
    Files.createDirectories(projectDirectory)

    //note: for testing locally on macOS use this link https://github.com/Virtuslab/scala-cli/releases/latest/download/scala-cli-aarch64-apple-darwin.gz"
    val curlCommand = Seq("curl", "--fail", "--location", "https://github.com/Virtuslab/scala-cli/releases/latest/download/scala-cli-x86_64-pc-linux.gz")
    val gzipCommand = "gzip --decompress"
    val curlProcess = Process(curlCommand) #| Process(gzipCommand, projectDirectory.toFile)

    val outputFile = projectDirectory.resolve("scala-cli").toFile

    val stderr = new StringBuilder
    val processChain = curlProcess #> outputFile #&& Process(s"chmod +x $outputFile")
    val setupScalaCliScript = Try(processChain! ProcessLogger(_ => (), stderr append _ + "\n"))
    setupScalaCliScript.ifNonZero(throw new Exception(s"Cannot install Scala CLI \n $stderr"))
  }
}