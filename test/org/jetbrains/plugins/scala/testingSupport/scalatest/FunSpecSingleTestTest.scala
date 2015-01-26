package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait FunSpecSingleTestTest extends IntegrationTest {
  def testFunSpec() {
    addFileToProject("FunSpecTest.scala",
      """
        |import org.scalatest._
        |
        |class FunSpecTest extends FunSpec {
        |  describe("FunSpecTest") {
        |    it ("should launch single test") {
        |      print(">>TEST: OK<<")
        |    }
        |
        |    it ("should not launch other tests") {
        |      print(">>TEST: FAILED<<")
        |    }
        |  }
        |}
      """.stripMargin
    )

    runTestByLocation(6, 9, "FunSpecTest.scala",
      checkConfigAndSettings(_, "FunSpecTest", "FunSpecTest should launch single test"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FunSpecTest", "FunSpecTest", "should launch single test") &&
          checkResultTreeDoesNotHaveNodes(root, "should not launch other tests"),
      debug = true
    )
  }
}
