package org.jetbrains.plugins.scala.testingSupport.scalatest.shell

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import org.jetbrains.plugins.scala.extensions.{PathExt, inWriteAction}
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.project.{ExternalSystemImportingTestCaseProxy, SbtCachesSetupUtil, SbtProjectSystem}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.concurrent.duration.DurationInt

/**
 * Base class for the tests which run a ScalaTest Run Configuration via an sbt shell.
 *
 * Sets up the sbt project the tests are run against and performs a real sbt project import, because the import
 * is required for sbt shell commands to include the correct project prefix (e.g. `{file:///...}root/ testOnly ...`).
 */
abstract class ScalaTestSbtShellTestBase extends ScalaTestTestCase {

  protected def sbtVersion: SbtVersion

  protected val funSuiteClassName: String = "FunSuiteTest"
  protected val funSuiteFileName: String = funSuiteClassName + ".scala"

  /** NOTE: the directory is shared by all the subclasses, so a single sbt project is reused by all of them. */
  private val testPath: Path =
    TestUtils.findCommunityRootPath / "scala" / "test-integration" / "testing-support" / "testData" / testDataDirectoryName / "ScalaTestWithSbtShellEnabledTestBase"

  override protected def usesManagedSourcesAndCompilation: Boolean = false

  override protected def getTestAppPath: String =
    testPath.toString

  override protected def srcPath: Path =
    testPath / "src" / "test" / "scala"

  override implicit def defaultTestOptions: TestRunOptions =
    TestRunOptions(120.seconds, 0)

  /** The real sbt module is set up after the import. */
  override protected def setUpModule(): Unit = ()

  override protected def setUp(): Unit = {
    val projectDir = testPath / "project"
    Files.createDirectories(projectDir)
    Files.writeString(
      projectDir / "build.properties",
      s"sbt.version=$sbtVersion",
      StandardCharsets.UTF_8,
    )
    super.setUp()
    importSbtProject()
  }

  private def importSbtProject(): Unit = {
    val settings = new SbtProjectSettings()
    settings.setExternalProjectPath(getTestAppPath)
    settings.jdk = getTestProjectJdk.getName

    SbtCachesSetupUtil.setupCoursierAndIvyCache(getProject)

    ExternalSystemImportingTestCaseProxy.importProject(
      getProject,
      SbtProjectSystem.Id,
      settings,
      getTestAppPath,
      ExternalSystemImportingTestCaseProxy.createImportSpec(getProject, SbtProjectSystem.Id),
      ExternalSystemImportingTestCaseProxy.handleImportFailure(_, _)
    )

    val modules = ModuleManager.getInstance(getProject).getModules
    val testModule = modules.find(_.getName.endsWith(".test")).getOrElse(
      throw new AssertionError("No .test module found after import")
    )
    myModule = testModule
  }

  override protected def tearDown(): Unit = {
    inWriteAction {
      val projectJdkTable = ProjectJdkTable.getInstance()
      projectJdkTable.getAllJdks.foreach(projectJdkTable.removeJdk)
    }
    super.tearDown()
  }
}
