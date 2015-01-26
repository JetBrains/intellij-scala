package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait FunSuiteSingleTestTest extends IntegrationTest {
  def testFunSuite() {
    addFileToProject("FunSuiteTest.scala",
      """
        |import org.scalatest._
        |
        |class FunSuiteTest extends FunSuite {
        |
        |  test("should not run other tests") {
        |    print(">>TEST: FAILED<<")
        |  }
        |
        |  test("should run single test") {
        |    print(">>TEST: OK<<")
        |  }
        |}
      """.stripMargin
    )

    runTestByLocation(9, 8, "FunSuiteTest.scala",
      checkConfigAndSettings(_, "FunSuiteTest", "should run single test"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FunSuiteTest", "should run single test") &&
          checkResultTreeDoesNotHaveNodes(root, "should not run other tests"),
      debug = true
    )
  }
}
