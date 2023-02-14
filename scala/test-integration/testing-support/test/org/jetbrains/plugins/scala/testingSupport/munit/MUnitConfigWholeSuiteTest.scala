package org.jetbrains.plugins.scala.testingSupport.munit

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude

class MUnitConfigWholeSuiteTest extends MUnitTestCase {

  private val ClassNameFunSuite = "MUnitConfigWholeSuite_Test_FunSuite"
  private val FileNameFunSuite = s"$ClassNameFunSuite.scala"

  addSourceFile(FileNameFunSuite,
    s"""import munit.FunSuite
       |
       |class $ClassNameFunSuite extends FunSuite {
       |  test("test success 1") {
       |  }
       |
       |  test("test success 2") {
       |  }
       |
       |  test("test success " + "dynamic") {
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

  private val ClassNameScalaCheckSuite = "MUnitConfigWholeSuite_Test_ScalaCheckSuite"
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
       |}
       |""".stripMargin
  )

  def testWholeSuite_FunSuite(): Unit =
    runTestByLocation2(loc(FileNameFunSuite, 2, 10),
      assertConfigAndSettings(_, ClassNameFunSuite),
      (testResult: TestRunResult) => {
        val root = testResult.requireTestTreeRoot

        assertResultTreePathsEqualsUnordered(root)(
          Seq(
            TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", s"$ClassNameFunSuite.test success 1"),
            TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", s"$ClassNameFunSuite.test success 2"),
            TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", s"$ClassNameFunSuite.test success dynamic"),
            TestNodePathWithStatus(Magnitude.FAILED_INDEX, "[root]", s"$ClassNameFunSuite.test failure JUnit assert"),
            TestNodePathWithStatus(Magnitude.FAILED_INDEX, "[root]", s"$ClassNameFunSuite.test failure MUnit assert"),
            TestNodePathWithStatus(Magnitude.ERROR_INDEX, "[root]", s"$ClassNameFunSuite.test error"),
          )
        )

        assertExitCode(-1, testResult)
      }
    )

  def testWholeSuite_ScalaCheckSuite(): Unit =
    runTestByLocation2(loc(FileNameScalaCheckSuite, 4, 10),
      assertConfigAndSettings(_, ClassNameScalaCheckSuite),
      (testResult: TestRunResult) => {
        val root = testResult.requireTestTreeRoot

        assertResultTreePathsEqualsUnordered(root)(
          Seq(
            TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", s"$ClassNameScalaCheckSuite.simple test"),
            TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", s"$ClassNameScalaCheckSuite.property test success"),
            TestNodePathWithStatus(Magnitude.FAILED_INDEX, "[root]", s"$ClassNameScalaCheckSuite.property test failure"),
            TestNodePathWithStatus(Magnitude.ERROR_INDEX, "[root]", s"$ClassNameScalaCheckSuite.property test error"),
          )
        )

        assertExitCode(-1, testResult)
      }
    )
}
