package org.jetbrains.plugins.scala.testingSupport.munit

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration
import org.jetbrains.plugins.scala.testingSupport.test.testdata.SingleTestData

//noinspection RedundantBlock
class MUnitConfigSingleTestTest extends MUnitTestCase {

  private val qqq = "\"\"\""

  private val ClassNameFunSuite = "MUnitConfigSingleTest_Test_FunSuite"
  private val FileNameFunSuite = s"$ClassNameFunSuite.scala"

  addSourceFile(FileNameFunSuite,
    s"""import munit.FunSuite
       |
       |class $ClassNameFunSuite extends FunSuite {
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

  def testFunSuite_Success1(): Unit =
    runTestByLocation2(
      loc(FileNameFunSuite, 3, 10),
      config => assertConfigAndSettings(config, ClassNameFunSuite, "test success 1"),
      root => assertResultTreePathsEqualsUnordered(root.testTreeRoot.get)(Seq(
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", ClassNameFunSuite, s"${ClassNameFunSuite}.test success 1")
      ))
    )

  def testFunSuite_Success2(): Unit =
    runTestByLocation2(
      loc(FileNameFunSuite, 6, 10),
      config => assertConfigAndSettings(config, ClassNameFunSuite, "test success 2 multiline quotes"),
      result => assertResultTreePathsEqualsUnordered(result.testTreeRoot.get)(Seq(
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", ClassNameFunSuite, s"${ClassNameFunSuite}.test success 2 multiline quotes")
      ))
    )

  def testFunSuite_FailureJUnit(): Unit =
    runTestByLocation2(
      loc(FileNameFunSuite, 9, 10),
      config => assertConfigAndSettings(config, ClassNameFunSuite, "test failure JUnit assert"),
      result => {
        assertResultTreePathsEqualsUnordered(result.testTreeRoot.get)(Seq(
          TestNodePathWithStatus(Magnitude.FAILED_INDEX, "[root]", ClassNameFunSuite, s"${ClassNameFunSuite}.test failure JUnit assert")
        ))
        assertExitCode(-1, result)
      }
    )

  def testFunSuite_FailureMUnit(): Unit =
    runTestByLocation2(
      loc(FileNameFunSuite, 13, 10),
      config => assertConfigAndSettings(config, ClassNameFunSuite, "test failure MUnit assert"),
      result => {
        assertResultTreePathsEqualsUnordered(result.testTreeRoot.get)(Seq(
          TestNodePathWithStatus(Magnitude.FAILED_INDEX, "[root]", ClassNameFunSuite, s"${ClassNameFunSuite}.test failure MUnit assert")
        ))
        assertExitCode(-1, result)
      }
    )

  def testFunSuite_Error(): Unit =
    runTestByLocation2(loc(FileNameFunSuite, 17, 10),
      config => assertConfigAndSettings(config, ClassNameFunSuite, "test error"),
      result => {
        assertResultTreePathsEqualsUnordered(result.testTreeRoot.get)(Seq(
          TestNodePathWithStatus(Magnitude.ERROR_INDEX, "[root]", ClassNameFunSuite, s"${ClassNameFunSuite}.test error")
        ))
        assertExitCode(-1, result)
      }
    )

  def testFunSuite_RunSelectedTests(): Unit = {
    // create single test
    val runConfig = createTestFromLocation(loc(FileNameFunSuite, 3, 10))

    val config = runConfig.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
    val newData = new SingleTestData(config)
    newData.testClassPath = config.testConfigurationData.asInstanceOf[SingleTestData].testClassPath
    newData.testName = Seq(s"test success 1", "test failure MUnit assert").mkString("\n")

    config.testConfigurationData = newData

    runTestByLocation3(
      runConfig,
      result => {
        assertResultTreePathsEqualsUnordered(result.testTreeRoot.get)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", ClassNameFunSuite, s"$ClassNameFunSuite.test success 1"),
          TestNodePathWithStatus(Magnitude.FAILED_INDEX, "[root]", ClassNameFunSuite, s"$ClassNameFunSuite.test failure MUnit assert"),
        ))
        assertExitCode(-1, result)
      }
    )
  }

  private val ClassNameScalaCheckSuite = "MUnitConfigSingleTest_Test_ScalaCheckSuite"
  private val FileNameScalaCheckSuite = ClassNameScalaCheckSuite + ".scala"

  addSourceFile(FileNameScalaCheckSuite,
    s"""import munit.ScalaCheckSuite
       |
       |import org.scalacheck.Prop.forAll
       |
       |class $ClassNameScalaCheckSuite extends ScalaCheckSuite {
       |  test("simple test") {
       |  }
       |
       |  property("property test success") {
       |    forAll { (n1: Int, n2: Int) => n1 + n2 == n2 + n1 }
       |  }
       |
       |  property("property test failure") {
       |    forAll { (n1: Int, n2: Int) => n1 + n2 == n2 + n1 + 42 }
       |  }
       |
       |  property("property test error") {
       |    1 / 0
       |    ???
       |  }
       |}""".stripMargin)

  def testScalaCheckSuite_SimpleTest(): Unit =
    runTestByLocation2(
      loc(FileNameScalaCheckSuite, 5, 10),
      config => assertConfigAndSettings(config, ClassNameScalaCheckSuite, "simple test"),
      root => assertResultTreePathsEqualsUnordered(root.testTreeRoot.get)(Seq(
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", ClassNameScalaCheckSuite, s"${ClassNameScalaCheckSuite}.simple test")
      ))
    )

  def testScalaCheckSuite_PropertyTestSuccess(): Unit =
    runTestByLocation2(
      loc(FileNameScalaCheckSuite, 8, 10),
      config => assertConfigAndSettings(config, ClassNameScalaCheckSuite, "property test success"),
      root => assertResultTreePathsEqualsUnordered(root.testTreeRoot.get)(Seq(
        TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", ClassNameScalaCheckSuite, s"${ClassNameScalaCheckSuite}.property test success")
      ))
    )

  def testScalaCheckSuite_PropertyTestFailure(): Unit =
    runTestByLocation2(
      loc(FileNameScalaCheckSuite, 12, 10),
      config => assertConfigAndSettings(config, ClassNameScalaCheckSuite, "property test failure"),
      root => assertResultTreePathsEqualsUnordered(root.testTreeRoot.get)(Seq(
        TestNodePathWithStatus(Magnitude.FAILED_INDEX, "[root]", ClassNameScalaCheckSuite, s"${ClassNameScalaCheckSuite}.property test failure")
      ))
    )

  def testScalaCheckSuite_PropertyTestError(): Unit =
    runTestByLocation2(
      loc(FileNameScalaCheckSuite, 16, 10),
      config => assertConfigAndSettings(config, ClassNameScalaCheckSuite, "property test error"),
      root => assertResultTreePathsEqualsUnordered(root.testTreeRoot.get)(Seq(
        TestNodePathWithStatus(Magnitude.ERROR_INDEX, "[root]", ClassNameScalaCheckSuite, s"${ClassNameScalaCheckSuite}.property test error")
      ))
    )

}
