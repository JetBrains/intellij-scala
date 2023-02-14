package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.testframework.AbstractTestProxy
import org.jetbrains.plugins.scala.testingSupport.IntegrationTestConfigurationRunning.foldText
import org.junit.Assert
import org.junit.Assert.fail

import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait IntegrationTestConfigurationRunning {

  implicit def defaultTestOptions: TestRunOptions = TestRunOptions(10.seconds, 0)

  case class TestRunOptions(duration: FiniteDuration, expectedProcessErrorCode: Int) {
    def withDuration(newDuration: FiniteDuration): TestRunOptions = copy(duration = newDuration)
    def withErrorCode(newCode: Int): TestRunOptions = copy(expectedProcessErrorCode = newCode)
  }

  protected def runTestFromConfig(
    runConfig: RunnerAndConfigurationSettings,
    duration: FiniteDuration
  ): TestRunResult

  case class ProcessOutput(
    text: String,
    textFromTests: String,
    smUncapturedText: String // text, not containing any service messages
  )

  case class TestRunResult(
    config: RunnerAndConfigurationSettings,
    processExitCode: Int,
    processOutput: ProcessOutput,
    testTreeRoot: Option[AbstractTestProxy]
  ) {
    def printOutputDetailsToConsole(): Unit = {
      println(outputDetails(fold = true))
    }

    def outputDetails(fold: Boolean): String = {
      def maybeFold(s: String) = if (fold) foldText(s) else s
      s"""Process exit code: $processExitCode
         |Process captured output:
         |${maybeFold(processOutput.text)}
         |Uncaptured output (without service messages):
         |${maybeFold(processOutput.smUncapturedText)}
         |""".stripMargin
    }

    def requireTestTreeRoot: AbstractTestProxy =
      testTreeRoot.getOrElse {
        fail(s"testTreeRoot not defined").asInstanceOf[Nothing]
      }
  }

  def assertExitCode(expectedCode: Int, runResult: TestRunResult): Unit =
    assertExitCode(expectedCode, runResult.processExitCode)

  def assertExitCode(expectedCode: Int, actualCode: Int): Unit = {
    // return code on Unix/Linux program is a single byte; it has a value between 0 and 255
    // -1 becomes 255, -2 becomes 254, etc...
    val expectedCodeFixed =
    if (com.intellij.openapi.util.SystemInfo.isUnix)
      expectedCode.toByte & 0xFF
    else
      expectedCode
    Assert.assertEquals(
      "Test runner process terminated with unexpected error code",
      expectedCodeFixed,
      actualCode
    )
  }
}

object IntegrationTestConfigurationRunning {
  /**
   * @see [[com.intellij.testFramework.TestLoggerFactory.FAILED_TEST_DEBUG_OUTPUT_MARKER]]
   * @see `com.intellij.testFramework.FailedTestDebugLogConsoleFolding`
   */
  private val FoldLineMarker = "\u2003"

  private def foldText(s: String): String =
    s.linesIterator.map(FoldLineMarker + _).mkString("\n")
}


