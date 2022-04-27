package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.testframework.AbstractTestProxy
import org.junit.Assert.fail

import scala.concurrent.duration.FiniteDuration

trait IntegrationTestConfigurationRunning {

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

  /**
   * @see [[com.intellij.testFramework.TestLoggerFactory.FAILED_TEST_DEBUG_OUTPUT_MARKER]]
   * @see `com.intellij.testFramework.FailedTestDebugLogConsoleFolding`
   */
  private val FoldLineMarker = "\u2003"
  private def foldText(s: String): String =
    s.linesIterator.map(FoldLineMarker + _).mkString("\n")
}


