package org.jetbrains.plugins.scala.testingSupport.scalatest.shell

import com.intellij.execution.RunnerAndConfigurationSettings
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration
import org.jetbrains.sbt.SbtVersion
import org.junit.Assert.assertTrue

/**
 * Tests for running a ScalaTest Run Configuration via an sbt shell with the sbt UI disabled.
 *
 * In this mode no test tree is built, and the raw sbt output is printed to the Run console,
 * so the Run console output is verified instead of the test tree.
 *
 * Covers three cases:
 *  - whole suite run
 *  - single test run (with `-- -t` filter)
 *  - package-level run (with multiple suites)
 *
 * @see [[ScalaTestWithSbtShellAndUiTestBase]] for the same runs with the sbt UI enabled
 */
abstract class ScalaTestWithSbtShellWithoutUiTestBase extends ScalaTestSbtShellTestBase {

  def testWholeSuite(): Unit =
    runTestWithoutSbtUi(
      config = createTestFromLocation(loc(funSuiteFileName, 2, 10)),
      s"$funSuiteClassName:",
      "- should not run other tests",
      "- should run single test",
      "- tagged",
      "Total number of tests run: 3",
      "Suites: completed 1, aborted 0",
      "Tests: succeeded 3, failed 0, canceled 0, ignored 0, pending 0",
      "All tests passed.",
    )

  def testSingleTest(): Unit =
    runTestWithoutSbtUi(
      config = createTestFromLocation(loc(funSuiteFileName, 6, 10)),
      s"$funSuiteClassName:",
      "- should run single test",
      "Total number of tests run: 1",
      "Suites: completed 1, aborted 0",
      "Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0",
      "All tests passed.",
    )

  def testPackage(): Unit =
    runTestWithoutSbtUi(
      config = createTestFromLocation(packageLoc("sbtTestPackage")),
      "SuiteA:",
      "- test from suite A",
      "SuiteB:",
      "- test from suite B",
      "All tests passed.",
    )

  /**
   * @param expectedOutputFragments the texts expected to be printed to the Run console
   */
  private def runTestWithoutSbtUi(config: RunnerAndConfigurationSettings, expectedOutputFragments: String*): Unit = {
    val runConfiguration = config.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
    runConfiguration.testConfigurationData.setUseSbt(true)
    runConfiguration.testConfigurationData.setUseUiWithSbt(false)

    runTestByLocation3(config, { result =>
      assertExitCode(0, result)

      val output = result.processOutput.text
      assertTrue(s"the Run console is empty:\n${result.outputDetails(fold = true)}", output.trim.nonEmpty)
      expectedOutputFragments.foreach { fragment =>
        assertTrue(s"the output doesn't contain '$fragment':\n$output", output.contains(fragment))
      }
    })
  }
}

class ScalaTestWithSbtShellWithoutUiTest_Sbt_1 extends ScalaTestWithSbtShellWithoutUiTestBase {
  override protected def sbtVersion: SbtVersion = SbtVersion.Latest.Sbt_1
}

class ScalaTestWithSbtShellWithoutUiTest_Sbt_2 extends ScalaTestWithSbtShellWithoutUiTestBase {
  override protected def sbtVersion: SbtVersion = SbtVersion.Latest.Sbt_2
}
