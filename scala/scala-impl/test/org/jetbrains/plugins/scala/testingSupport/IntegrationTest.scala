package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.configurations.TestLocation
import org.jetbrains.plugins.scala.configurations.TestLocation.CaretLocation
import org.jetbrains.plugins.scala.extensions.{PsiNamedElementExt, inReadAction}
import org.junit.Assert._

import java.nio.file.Path
import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait IntegrationTest extends AnyRef
  with IntegrationTestConfigurationCreation
  with IntegrationTestConfigAssertions
  with IntegrationTestTreeViewAssertions
  with IntegrationTestConfigurationRunning
  with IntegrationTestRunResultAssertions {

  protected def getProject: Project
  protected def srcPath: Path

  implicit def defaultTestOptions: TestRunOptions = TestRunOptions(10.seconds, 0)

  case class TestRunOptions(duration: FiniteDuration, expectedProcessErrorCode: Int) {
    def withDuration(newDuration: FiniteDuration): TestRunOptions = copy(duration = newDuration)
    def withErrorCode(newCode: Int): TestRunOptions = copy(expectedProcessErrorCode = newCode)
  }

  protected def IgnoreTreeResult: TestTreeAssert = _ => ()
  protected def IgnoreProcessOutput: ProcessOutputAssert  = _ => ()

  def AssertTestOutputTextContains(expectedText: String): ProcessOutputAssert  = { output =>
    val res = output.textFromTests
    assertTrue(s"output was '$res' expected to contain '$expectedText'", res.contains(expectedText))
  }

  def runTestByLocation(
    testLocation: TestLocation,
    assertConfig: ConfigurationAssert,
    assertTestTree: TestTreeAssert,
  )(implicit testOptions: TestRunOptions): Unit =
    runTestByLocation(
      testLocation,
      assertConfig,
      assertTestTree,
      IgnoreProcessOutput
    )(testOptions)

  def runTestByLocation(
    testLocation: TestLocation,
    assertConfig: ConfigurationAssert,
    assertTestTree: TestTreeAssert,
    assertProcessOutput: ProcessOutputAssert
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
    assertConfig: ConfigurationAssert,
    assertTestResult: TestRunResultAssert
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
    assertTestResult: TestRunResultAssert
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

  def runGoToSourceTest(
    caretLocation: CaretLocation,
    assertConfiguration: ConfigurationAssert,
    testPath: TestNodePath,
    sourceLine: Int,
    sourceFile: Option[String] = None
  )(implicit testOptions: TestRunOptions): Unit =
    runGoToSourceTest(
      caretLocation,
      assertConfiguration,
      testPath,
      GoToLocation(sourceFile.getOrElse(caretLocation.fileName), sourceLine)
    )(testOptions)

  case class GoToLocation(sourceFile: String, sourceLine: Int)

  def runGoToSourceTest(
    caretLocation: CaretLocation,
    assertConfiguration: ConfigurationAssert,
    testPath: TestNodePath,
    expectedLocation: GoToLocation
  )(implicit testOptions: TestRunOptions): Unit = {
    val runConfig = createTestFromCaretLocation(caretLocation)

    assertConfiguration(runConfig)

    val runResult = runTestFromConfig(runConfig, testOptions.duration)

    val testTreeRoot = runResult.requireTestTreeRoot
    assertGoToSourceTest(testTreeRoot, testPath, expectedLocation)
  }

  protected def AssertGoToSourceTest(
    testPath: TestNodePath,
    expectedLocation: GoToLocation
  ): TestTreeAssert = { testRoot =>
    assertGoToSourceTest(testRoot, testPath, expectedLocation)
  }

  protected def assertGoToSourceTest(
    testRoot: AbstractTestProxy,
    testPath: TestNodePath,
    expectedLocation: GoToLocation
  ): Unit = inReadAction {
    val testPathOpt = getExactNamePathFromResultTree(testRoot, testPath, allowTail = true)

    val project = getProject

    val psiElement = {
      val leafNode = testPathOpt.nodes.last
      val location = leafNode.getLocation(project, GlobalSearchScope.projectScope(project))
      assertNotNull(s"location should not be null for leaf node: $leafNode", location)
      location.getPsiElement
    }

    val psiFile = psiElement.getContainingFile
    val textRange = psiElement.getTextRange

    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
    assertEquals(expectedLocation.sourceFile, psiFile.name)

    val startLineNumber = document.getLineNumber(textRange.getStartOffset)
    assertEquals(expectedLocation.sourceLine, startLineNumber)
  }
}
