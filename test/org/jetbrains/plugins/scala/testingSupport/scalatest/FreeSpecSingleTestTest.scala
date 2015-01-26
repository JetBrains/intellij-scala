package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait FreeSpecSingleTestTest extends IntegrationTest {
  def testFreeSpec() {
    addFileToProject("FreeSpecTest.scala",
      """
        |import org.scalatest._
        |
        |class FreeSpecTest extends FreeSpec {
        |  "A FreeSpecTest" - {
        |    "should be able to run single tests" in {
        |      print(">>TEST: OK<<")
        |    }
        |
        |    "should not run tests that are not selected" in {
        |      print(">>TEST: FAILED<<")
        |    }
        |  }
        |}
      """.stripMargin
    )

    runTestByLocation(6, 3, "FreeSpecTest.scala",
      checkConfigAndSettings(_, "FreeSpecTest", "A FreeSpecTest should be able to run single tests"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FreeSpecTest", "A FreeSpecTest", "should be able to run single tests") &&
          checkResultTreeDoesNotHaveNodes(root, "should not run tests that are not selected"),
      debug = true
    )
  }
}
