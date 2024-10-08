package org.jetbrains.scalaCli.project

import com.intellij.ide.projectWizard.NewProjectWizardConstants
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener, ExternalSystemTaskType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.FixtureRuleKt.useProject
import com.intellij.testFramework.JUnit38AssumeSupportRunner
import org.jetbrains.bsp.BSP
import org.jetbrains.bsp.protocol.BspCommunicationService
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.sbt.project.{ExactMatch, NewScalaProjectWizardTestBase, ProjectStructureTestUtils}
import org.jetbrains.sbt.project.ProjectStructureDsl._
import org.jetbrains.sbt.project.ProjectStructureMatcher.ProjectComparisonOptions
import org.jetbrains.sbt.project.template.wizard.buildSystem.BuildSystemScalaNewProjectWizardData.scalaBuildSystemData
import org.jetbrains.sbt.project.template.wizard.buildSystem.ScalaNewProjectWizardData.scalaData
import org.junit.Assume
import org.junit.runner.RunWith

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.sys.process.{Process, ProcessLogger}
import scala.util.Try


// TODO:
//  - test .gitignore creation
//  - check added sample code
/**
 *  The Scala CLI tests are executed only on Linux or macOS machines; on other systems, they are ignored.
 */
@RunWith(classOf[JUnit38AssumeSupportRunner])
class NewScalaCliProjectWizardTest extends NewScalaProjectWizardTestBase with ExactMatch {

  /**
   * All tests in this class have the same project name.
   * This is just a simplification, so that in the #setUp method, the Scala CLI run script can be placed in the right place.
   */
  private val projectName = "scalaCliProjectName"

  override protected def setUp(): Unit = {
    ignoreTestIfSystemIsNotAllowed()
    super.setUp()
    installScalaCli()
    // note: it's a workaround for #SCL-23061.
    // Closing all BSP sessions cannot be achieved by overriding #waitForConfiguration and closing the sessions there,
    // because the project refresh in ModuleBuilderUtil.doSetupModule is executed asynchronously.

    // (!!) It may happen that in the testing terminal something like this will appear:
    // Caused by: java.lang.RuntimeException: BSP server not initialized yet
    // It's caused by "build/exit" request, which is called when closing all BSP sessions.
    // This issue also occurs in production if you click "close" on the BSP session quickly after the reload (this usually happens on the first reload).
    // However, it is not reported to the users.
    // It seems to me as a bug on the server side.
    // The bsp server should be initialized - BSP sessions are closed after the reload,
    // so the endpoint to download all targets has already been triggered, and the response has been received.
    val closeAllBspInstancesAfterReload = new ExternalSystemTaskNotificationListener {
      override def onEnd(id: ExternalSystemTaskId): Unit = {
        val isProjectResolveTask = id.getType == ExternalSystemTaskType.RESOLVE_PROJECT
        if (isProjectResolveTask) {
          BspCommunicationService.getInstance.closeAll
        }
      }
    }
    ExternalSystemTaskNotificationListener.EP_NAME.getPoint.registerExtension(closeAllBspInstancesAfterReload, getTestRootDisposable)
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
    val project = createScalaProject(NewProjectWizardConstants.Language.SCALA, projectName, checkJDK = false) { step =>
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

    //note: only Linux and macOS systems are allowed
    val archiveName =
      if (SystemInfo.isLinux) "scala-cli-x86_64-pc-linux.gz"
      else "scala-cli-aarch64-apple-darwin.gz"

    val curlCommand = s"curl --fail --location https://github.com/Virtuslab/scala-cli/releases/latest/download/$archiveName"
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
   * Ignores test if the operating system is not Linux or Mac
   */
  private def ignoreTestIfSystemIsNotAllowed(): Unit = {
    val isAllowed = SystemInfo.isLinux || SystemInfo.isMac
    Assume.assumeTrue("The operating system is not allowed (Linux/macOS)", isAllowed)
  }
}