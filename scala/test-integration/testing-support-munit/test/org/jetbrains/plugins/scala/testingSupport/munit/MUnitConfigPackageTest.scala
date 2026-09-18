package org.jetbrains.plugins.scala.testingSupport.munit

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.{JavaCommandLineState, JavaParameters}
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiPackage
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.testingSupport.test.munit.MUnitConfiguration
import org.jetbrains.plugins.scala.testingSupport.test.testdata.AllInPackageTestData
import org.jetbrains.plugins.scala.util.assertions.ExceptionAssertions
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions.ObjectOps
import org.junit.Assert.{assertFalse, assertTrue}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

/** Covers package configurations, background command-line preparation, and MUnit result trees. */
abstract class MUnitConfigPackageTestBase extends MUnitTestCase {

  private val packageName0 = "org"
  private val packageName1 = "org.example1"
  private val packageName2 = "org.example2"

  private def classWithFileName(packageName: String, className: String): (String, String) = {
    val pathPrefix = packageName.replace('.', '/')
    (className, s"$pathPrefix/$className.scala")
  }

  protected val (className01, fileName01) = classWithFileName(packageName0, "MyTest01")
  protected val (className02, fileName02) = classWithFileName(packageName0, "MyTest02")

  protected val (className11, fileName11) = classWithFileName(packageName1, "MyTest11")
  protected val (className12, fileName12) = classWithFileName(packageName1, "MyTest12")

  protected val (className21, fileName21) = classWithFileName(packageName2, "MyTest21")
  protected val (className22, fileName22) = classWithFileName(packageName2, "MyTest22")

  private def testClassContent(packageName: String, className: String, testNameSuffix: String) =
    s"""package $packageName
       |
       |import munit.FunSuite
       |
       |class $className extends FunSuite {
       |  test("test success $testNameSuffix") {
       |  }
       |  test("test error $testNameSuffix") {
       |    assertEquals(1, 2)
       |  }
       |}
       |""".stripMargin

  addSourceFile(fileName01, testClassContent(packageName0, className01, "01"))
  addSourceFile(fileName02, testClassContent(packageName0, className02, "02"))

  addSourceFile(fileName11, testClassContent(packageName1, className11, "11"))
  addSourceFile(fileName12, testClassContent(packageName1, className12, "12"))

  addSourceFile(fileName21, testClassContent(packageName2, className21, "21"))
  addSourceFile(fileName22, testClassContent(packageName2, className22, "22"))

  private val optionsWithErrorCode = defaultTestOptions.withErrorCode(-1)

  def testPackage0(): Unit =
    runTestByLocation2(
      packageLoc(packageName0),
      config => {
        assertRunConfigTestPackage(config, packageName0)
        assertRunConfigName(config, "MUnit in 'org'")
        assertPackageCommandLineWithoutReadAccess(config, packageName0, Set(
          s"$packageName0.$className01", s"$packageName0.$className02",
          s"$packageName1.$className11", s"$packageName1.$className12",
          s"$packageName2.$className21", s"$packageName2.$className22",
        ))
      },
      root => assertResultTreePathsEqualsUnordered(root.testTreeRoot.get)(Seq(
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest01 / MyTest01.test error 01")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest01 / MyTest01.test success 01")),
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest02 / MyTest02.test error 02")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest02 / MyTest02.test success 02")),
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest11 / MyTest11.test error 11")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest11 / MyTest11.test success 11")),
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest12 / MyTest12.test error 12")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest12 / MyTest12.test success 12")),
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest21 / MyTest21.test error 21")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest21 / MyTest21.test success 21")),
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest22 / MyTest22.test error 22")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest22 / MyTest22.test success 22")),
      ))
    )(optionsWithErrorCode)

  def testPackage1(): Unit =
    runTestByLocation2(
      packageLoc(packageName1),
      config => {
        assertRunConfigTestPackage(config, packageName1)
        assertRunConfigName(config, "MUnit in 'example1'")
        assertPackageCommandLineWithoutReadAccess(config, packageName1, Set(
          s"$packageName1.$className11", s"$packageName1.$className12",
        ))
      },
      root => assertResultTreePathsEqualsUnordered(root.testTreeRoot.get)(Seq(
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest11 / MyTest11.test error 11")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest11 / MyTest11.test success 11")),
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest12 / MyTest12.test error 12")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest12 / MyTest12.test success 12"))
      ))
    )(optionsWithErrorCode)

  def testPackage2(): Unit =
    runTestByLocation2(
      packageLoc(packageName2),
      config => {
        assertRunConfigTestPackage(config, packageName2)
        assertRunConfigName(config, "MUnit in 'example2'")
        assertPackageCommandLineWithoutReadAccess(config, packageName2, Set(
          s"$packageName2.$className21", s"$packageName2.$className22",
        ))
      },
      root => assertResultTreePathsEqualsUnordered(root.testTreeRoot.get)(Seq(
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest21 / MyTest21.test error 21")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest21 / MyTest21.test success 21")),
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest22 / MyTest22.test error 22")),
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, TestNodePath.parse("[root] / MyTest22 / MyTest22.test success 22")),
      ))
    )(optionsWithErrorCode)

  private def assertPackageCommandLineWithoutReadAccess(
    settings: RunnerAndConfigurationSettings,
    packageName: String,
    expectedSuites: Set[String],
  ): Unit = {
    val configuration = settings.getConfiguration.assertInstanceOf[MUnitConfiguration]
    withReadAccessCheckingPackageData(configuration, packageName) {
      val state = createFreshCommandLineState(configuration)
      val parameters = prepareJavaParametersOffEdtWithoutReadAccess(state)
      assertPackageSuiteSelection(parameters, packageName, expectedSuites)
    }
  }

  private final class ReadAccessCheckingPackageData(configuration: MUnitConfiguration)
    extends AllInPackageTestData(configuration) {

    override def getPackage(path: String): PsiPackage = {
      // Cached package lookups do not reliably assert read access on every platform version.
      assertTrue("Package lookup must hold read access", ApplicationManager.getApplication.isReadAccessAllowed)
      super.getPackage(path)
    }
  }

  private def withReadAccessCheckingPackageData(
    configuration: MUnitConfiguration,
    packageName: String,
  )(body: => Unit): Unit = {
    val originalData = configuration.testConfigurationData
    val checkedData = new ReadAccessCheckingPackageData(configuration)
    checkedData.copyCommonFieldsFrom(originalData)
    checkedData.testPackagePath = packageName
    configuration.testConfigurationData = checkedData
    try {
      body
    } finally {
      configuration.testConfigurationData = originalData
    }
  }

  private def createFreshCommandLineState(configuration: MUnitConfiguration): JavaCommandLineState =
    inReadAction {
      // The execution test helper otherwise prepares and caches parameters on the EDT before launching the runner.
      new ExecutionEnvironmentBuilder(getProject, DefaultRunExecutor.getRunExecutorInstance)
        .runProfile(configuration)
        .build()
        .getState
        .assertInstanceOf[JavaCommandLineState]
    }

  private def prepareJavaParametersOffEdtWithoutReadAccess(state: JavaCommandLineState): JavaParameters = {
    // SCL-25929: discovery must acquire read access when command-line preparation starts without it.
    val application = ApplicationManager.getApplication
    assertFalse("Regression setup: parameter preparation must run off the EDT", application.isDispatchThread)
    assertFalse("Regression setup: parameter preparation must start without read access", application.isReadAccessAllowed)
    state.getJavaParameters
  }

  private def assertPackageSuiteSelection(
    parameters: JavaParameters,
    packageName: String,
    expectedSuites: Set[String],
  ): Unit = {
    // Complement the read-access regression check by verifying the suites passed to the JUnit runner.
    val arguments = parameters.getProgramParametersList.getList.asScala.toSeq
    assertTrue("MUnit must use the JUnit 4 runner", arguments.contains("-junit4"))
    val suitesFileArgument = arguments.filter(a => a.startsWith("@") && !a.startsWith("@w@"))
    assertEquals("Expected one suite-list file", 1, suitesFileArgument.size)
    val lines = Files.readAllLines(Path.of(suitesFileArgument.head.stripPrefix("@"))).asScala.toSeq
    assertEquals("Suite-list package", packageName, lines.head)
    // JUnitStarter writes package, category, and filters before the fully qualified suite names.
    assertEquals(s"Suites selected in $packageName", expectedSuites, lines.drop(3).toSet)
  }

  def testPackage_EnsureAssertionFails(): Unit = ExceptionAssertions.assertException[java.lang.AssertionError] {
    runTestByLocation2(
      packageLoc(packageName2),
      config => {
        assertRunConfigTestPackage(config, packageName2)
        assertRunConfigName(config, "")
      },
      root => assertResultTreePathsEqualsUnordered(root.testTreeRoot.get)(Seq(
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, TestNodePath.parse("[root] / MyTest21 / MyTest21.test error 21"))
      ))
    )(optionsWithErrorCode)
  }
}

class Munit_0_7_ConfigPackageTest extends MUnitConfigPackageTestBase with MUnit_0_7

class Munit_1_0_ConfigPackageTest extends MUnitConfigPackageTestBase with MUnit_1_0
