package org.jetbrains.plugins.scala.testingSupport.scalatest.shell

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import org.jetbrains.plugins.scala.extensions.{PathExt, inWriteAction}
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.project.{ExternalSystemImportingTestCaseProxy, SbtCachesSetupUtil, SbtProjectSystem}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.concurrent.duration.DurationInt

/**
 * Tests for running ScalaTest Run Configuration via an sbt shell with UI, verifying the test tree nodes produced by the IDE.
 *
 * Performs a real sbt project import, because it's required for sbt shell
 * commands to include the correct project prefix (e.g. `{file:///...}root/ testOnly ...`).
 *
 * Covers three cases:
 *  - whole suite run
 *  - single test run (with `-- -t` filter)
 *  - package-level run (with multiple suites)
 *
 * NOTE: Despite these tests passing, in real IntelliJ usage the test tree nodes are sometimes
 * rendered incorrectly. From what I've observed, the root cause might be that the sbt output is sometimes
 * rendered in a way that one line of sbt output is displayed as two lines in IntelliJ, which then breaks
 * the regexes in [[org.jetbrains.plugins.scala.testingSupport.test.sbt.ReportingSbtTestEventHandler]].
 * This should be investigated in SCL-24493.
 */
abstract class ScalaTestWithSbtShellEnabledAndImportedTestBase extends ScalaTestTestCase {

  protected def sbtVersion: SbtVersion

  private val funSuiteClassName = "FunSuiteTest"
  private val funSuiteFileName = funSuiteClassName + ".scala"

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

  private def testPath: Path =
    TestUtils.findCommunityRootPath / "scala" / "test-integration" / "testing-support" / "testData" / testDataDirectoryName / "ScalaTestWithSbtShellEnabledTestBase"

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

  def testWholeSuite(): Unit =
    runTest(
      config = createTestFromLocation(loc(funSuiteFileName, 2, 10)),
      TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSuiteClassName, "should not run other tests"),
      TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSuiteClassName, "should run single test"),
      TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSuiteClassName, "tagged"),
    )

  def testSingleTest(): Unit =
    runTest(
      config = createTestFromLocation(loc(funSuiteFileName, 6, 10)),
      TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSuiteClassName, "should run single test"),
    )

  def testPackage(): Unit =
    runTest(
      config = createTestFromLocation(packageLoc("sbtTestPackage")),
      TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "SuiteA", "test from suite A"),
      TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "SuiteB", "test from suite B"),
    )

  private def runTest(config: RunnerAndConfigurationSettings, expectedPaths: TestNodePathWithStatus*): Unit = {
    val runConfiguration = config.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
    runConfiguration.testConfigurationData.setUseSbt(true)
    runConfiguration.testConfigurationData.setUseUiWithSbt(true)

    runTestByLocation3(config, { result =>
      val root = result.requireTestTreeRoot
      assertResultTreePathsEqualsUnordered(root)(expectedPaths)
    })
  }
}

class ScalaTestWithSbtShellEnabledAndImportedTest_Sbt_1 extends ScalaTestWithSbtShellEnabledAndImportedTestBase {
  override protected def sbtVersion: SbtVersion = SbtVersion.Latest.Sbt_1
}

class ScalaTestWithSbtShellEnabledAndImportedTest_Sbt_2 extends ScalaTestWithSbtShellEnabledAndImportedTestBase {
  override protected def sbtVersion: SbtVersion = SbtVersion.Latest.Sbt_2
}
