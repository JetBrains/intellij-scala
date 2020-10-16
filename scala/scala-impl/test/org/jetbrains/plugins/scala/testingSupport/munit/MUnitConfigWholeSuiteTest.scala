package org.jetbrains.plugins.scala.testingSupport.munit

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude

class MUnitConfigWholeSuiteTest extends MUnitTestCase {

  protected val ClassName = "MUnitConfigWholeSuiteTest"
  protected val FileName = s"$ClassName.scala"

  addSourceFile(FileName,
    s"""import munit.FunSuite
       |
       |class $ClassName extends FunSuite {
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

  def testWholeSuite(): Unit =
    runTestByLocation2(loc(FileName, 2, 10),
      assertConfigAndSettings(_, ClassName),
      (testResult: TestRunResult) => {
        val root = testResult.requireTestTreeRoot

        assertResultTreePathsEqualsUnordered2(root)(
          Seq(
            TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", s"$ClassName.test success 1"),
            TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", s"$ClassName.test success 2"),
            TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", s"$ClassName.test success dynamic"),
            TestNodePathWithStatus(Magnitude.FAILED_INDEX, "[root]", s"$ClassName.test failure JUnit assert"),
            TestNodePathWithStatus(Magnitude.ERROR_INDEX, "[root]", s"$ClassName.test failure MUnit assert"),
            TestNodePathWithStatus(Magnitude.ERROR_INDEX, "[root]", s"$ClassName.test error"),
          )
        )

        assertExitCode(-1, testResult)
      }
    )
}
