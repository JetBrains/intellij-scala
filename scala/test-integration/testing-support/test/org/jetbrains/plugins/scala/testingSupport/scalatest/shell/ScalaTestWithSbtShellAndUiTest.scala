package org.jetbrains.plugins.scala.testingSupport.scalatest.shell

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration
import org.jetbrains.sbt.SbtVersion

/**
 * Tests for running a ScalaTest Run Configuration via an sbt shell with the sbt UI enabled,
 * verifying the test tree nodes produced by the IDE.
 *
 * Covers three cases:
 *  - whole suite run
 *  - single test run (with `-- -t` filter)
 *  - package-level run (with multiple suites)
 *
 * NOTE: Despite these tests passing, in real IntelliJ usage the test tree nodes are sometimes
 * rendered incorrectly. This should be investigated in SCL-24493.
 *
 * @see [[ScalaTestWithSbtShellWithoutUiTestBase]] for the same runs with the sbt UI disabled
 */
abstract class ScalaTestWithSbtShellAndUiTestBase extends ScalaTestSbtShellTestBase {

  def testWholeSuite(): Unit =
    runTestWithSbtUi(
      config = createTestFromLocation(loc(funSuiteFileName, 2, 10)),
      TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSuiteClassName, "should not run other tests"),
      TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSuiteClassName, "should run single test"),
      TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSuiteClassName, "tagged"),
    )

  def testSingleTest(): Unit =
    runTestWithSbtUi(
      config = createTestFromLocation(loc(funSuiteFileName, 6, 10)),
      TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSuiteClassName, "should run single test"),
    )

  def testPackage(): Unit =
    runTestWithSbtUi(
      config = createTestFromLocation(packageLoc("sbtTestPackage")),
      TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "SuiteA", "test from suite A"),
      TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "SuiteB", "test from suite B"),
    )

  /**
   * @param expectedPaths the test tree nodes expected to be built from the sbt output
   */
  private def runTestWithSbtUi(config: RunnerAndConfigurationSettings, expectedPaths: TestNodePathWithStatus*): Unit = {
    val runConfiguration = config.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
    runConfiguration.testConfigurationData.setUseSbt(true)
    runConfiguration.testConfigurationData.setUseUiWithSbt(true)

    runTestByLocation3(config, { result =>
      val root = result.requireTestTreeRoot
      assertResultTreePathsEqualsUnordered(root)(expectedPaths)
    })
  }
}

class ScalaTestWithSbtShellAndUiTest_Sbt_1 extends ScalaTestWithSbtShellAndUiTestBase {
  override protected def sbtVersion: SbtVersion = SbtVersion.Latest.Sbt_1
}

class ScalaTestWithSbtShellAndUiTest_Sbt_2 extends ScalaTestWithSbtShellAndUiTestBase {
  override protected def sbtVersion: SbtVersion = SbtVersion.Latest.Sbt_2
}
