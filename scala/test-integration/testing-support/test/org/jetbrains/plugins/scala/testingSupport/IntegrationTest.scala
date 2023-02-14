package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.configurations.TestLocation
import org.jetbrains.plugins.scala.configurations.TestLocation.CaretLocation
import org.junit.Assert._

import java.nio.file.Path

trait IntegrationTest extends AnyRef
  with IntegrationTestConfigurationCreation
  with IntegrationTestConfigurationRunning
  with IntegrationTestGoToTests
  with IntegrationTestConfigAssertions
  with IntegrationTestTreeViewAssertions
  with IntegrationTestRunResultAssertions {

  protected def getProject: Project
  protected def srcPath: Path

  protected val IgnoreTreeResult: AbstractTestProxy => Unit = _ => ()
  protected val IgnoreProcessOutput: ProcessOutput => Unit  = _ => ()

  def assertTestOutputTextContains(expectedText: String, output: ProcessOutput): Unit  = {
    val res = output.textFromTests
    assertTrue(s"output was '$res' expected to contain '$expectedText'", res.contains(expectedText))
  }

  def runTestByLocation(
    testLocation: TestLocation,
    assertConfig: RunnerAndConfigurationSettings => Unit,
    assertTestTree: AbstractTestProxy => Unit,
  )(implicit testOptions: TestRunOptions): Unit =
    runTestByLocation(
      testLocation,
      assertConfig,
      assertTestTree,
      IgnoreProcessOutput
    )(testOptions)

  def runTestByLocation(
    testLocation: TestLocation,
    assertConfig: RunnerAndConfigurationSettings => Unit,
    assertTestTree: AbstractTestProxy => Unit,
    assertProcessOutput: ProcessOutput => Unit
  )(implicit testOptions: TestRunOptions): Unit = {

    def assertTestResult(testRunResult: TestRunResult): Unit = {
      assertExitCode(testOptions.expectedProcessErrorCode, testRunResult)
      assertTestTree(testRunResult.requireTestTreeRoot)
      assertProcessOutput(testRunResult.processOutput)
    }

    runTestByLocation2(
      testLocation,
      assertConfig,
      assertTestResult
    )(testOptions)
  }

  def runTestByLocation2(
    testLocation: TestLocation,
    assertConfig: RunnerAndConfigurationSettings => Unit,
    assertTestResult: TestRunResult => Unit
  )(implicit testOptions: TestRunOptions): Unit = {
    try {
      val runConfig = createTestFromLocation(testLocation)
      assertConfig(runConfig)
      runTestByLocation3(runConfig, assertTestResult)(testOptions)
    } catch {
      case ex: AssertionError =>
        val filePath = testLocation match {
          case CaretLocation(fileName, _, _) => Some(srcPath.resolve(fileName).toFile)
          case TestLocation.CaretLocation2(virtualVile, _, _) => Some(virtualVile.toNioPath.toFile)
          case TestLocation.PsiElementLocation(psiElement) => Option(psiElement.getContainingFile).map(_.getVirtualFile.toNioPath.toFile)
          case _ => None
        }
        filePath.foreach(f => System.err.println(s"Test file path: $f"))
        throw ex
    }
  }

  def runTestByLocation3(
    runConfig: RunnerAndConfigurationSettings,
    assertTestResult: TestRunResult => Unit
  )(implicit testOptions: TestRunOptions): Unit = {
    val testRunResult = runTestFromConfig(runConfig, testOptions.duration)
    try
      assertTestResult(testRunResult)
    catch {
      case error: AssertionError =>
        testRunResult.printOutputDetailsToConsole()
        throw error
    }
  }

  def runDuplicateConfigTest(
    lineNumber: Int,
    offset: Int,
    fileName: String,
    assertConfigurationCheck: RunnerAndConfigurationSettings => Unit
  ): Unit = {
    runDuplicateConfigTest(loc(fileName, lineNumber, offset), assertConfigurationCheck)
  }

  def runDuplicateConfigTest(
    caretLocation: CaretLocation,
    assertConfigurationCheck: RunnerAndConfigurationSettings => Unit
  ): Unit = {
    val config1 = createTestFromCaretLocation(caretLocation)
    val config2 = createTestFromCaretLocation(caretLocation)
    assertConfigurationCheck(config1)
    assertConfigurationCheck(config2)
    assertEquals(config1.getName, config2.getName)
    assertEquals(config1.getType, config2.getType)
    assertEquals(config1.getFolderName, config2.getFolderName)
    assertEquals(config1.getConfiguration.getName, config2.getConfiguration.getName)
  }

}
