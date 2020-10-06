package org.jetbrains.plugins.scala.testingSupport.munit

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration
import org.jetbrains.plugins.scala.testingSupport.test.testdata.SingleTestData

//noinspection RedundantBlock
class MUnitConfigSingleTestTest extends MUnitTestCase {

  protected val ClassName = "MUnitConfigSingleTestTest"
  protected val FileName = s"$ClassName.scala"

  private val qqq = "\"\"\""

  addSourceFile(FileName,
    s"""import munit.FunSuite
       |
       |class $ClassName extends FunSuite {
       |  test("test success 1") {
       |  }
       |
       |  test(${qqq}test success 2 multiline quotes${qqq}) {
       |  }
       |
       |  test("test failure JUnit assert") {
       |    org.junit.Assert.assertEquals(1, 2)
       |  }
       |
       |  test("test failure MUnit assert") {
       |    this.assertEquals(1, 2)
       |  }
       |
       |  test("test error") {
       |    println(1 / 0)
       |  }
       |}""".stripMargin)

  def testSuccess1(): Unit =
    runTestByLocation2(
      loc(FileName, 3, 10),
      AssertConfigAndSettings(ClassName, "test success 1"),
      AssertResultTreePathsEqualsUnordered2(Seq(
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", ClassName, s"${ClassName}.test success 1")
      ))
    )

  def testSuccess2(): Unit =
    runTestByLocation2(
      loc(FileName, 6, 10),
      AssertConfigAndSettings(ClassName, "test success 2 multiline quotes"),
      AssertResultTreePathsEqualsUnordered2(Seq(
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", ClassName, s"${ClassName}.test success 2 multiline quotes")
      ))
    )

  def testFailureJUnit(): Unit =
    runTestByLocation2(
      loc(FileName, 9, 10),
      AssertConfigAndSettings(ClassName, "test failure JUnit assert"),
      AssertResultTreePathsEqualsUnordered2(Seq(
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, "[root]", ClassName, s"${ClassName}.test failure JUnit assert")
      )).and (
        AssertExitCode(-1)
      )
    )

  def testFailureMUnit(): Unit =
    runTestByLocation2(
      loc(FileName, 13, 10),
      AssertConfigAndSettings(ClassName, "test failure MUnit assert"),
      AssertResultTreePathsEqualsUnordered2(Seq(
        TestNodePathWithStatus(Magnitude.ERROR_INDEX, "[root]", ClassName, s"${ClassName}.test failure MUnit assert")
      )).and (
        AssertExitCode(-1)
      )
    )

  def testError(): Unit =
    runTestByLocation2(loc(FileName, 17, 10),
      AssertConfigAndSettings(ClassName, "test error"),
      AssertResultTreePathsEqualsUnordered2(Seq(
        TestNodePathWithStatus(Magnitude.ERROR_INDEX, "[root]", ClassName, s"${ClassName}.test error")
      )).and (
        AssertExitCode(-1)
      )
    )

  def testRunSelectedTests(): Unit = {
    // create single test
    val runConfig = createTestFromLocation(loc(FileName, 3, 10))

    val config = runConfig.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
    val newData = new SingleTestData(config)
    newData.testClassPath = config.testConfigurationData.asInstanceOf[SingleTestData].testClassPath
    newData.testName = Seq(s"test success 1", "test failure MUnit assert").mkString("\n")

    config.testConfigurationData = newData

    runTestByLocation3(
      runConfig,
      AssertResultTreePathsEqualsUnordered2(Seq(
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", ClassName, s"$ClassName.test success 1"),
        TestNodePathWithStatus(Magnitude.ERROR_INDEX, "[root]", ClassName, s"$ClassName.test failure MUnit assert"),
      )).and (
        AssertExitCode(-1)
      )
    )
  }
}
