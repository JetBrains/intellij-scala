package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait FreeSpecPathSingleTestTest extends IntegrationTest {
  def testFreeSpecPath() {
    addFileToProject("FreeSpecPathTest.scala",
      """
        |import org.scalatest._
        |
        |class FreeSpecPathTest extends path.FreeSpec {
        |  "A FreeSpecTest" - {
        |    "should be able to run single test" in {
        |      print(">>TEST: OK<<")
        |    }
        |
        |    "should not run tests that are not selected" in {
        |      print("nothing interesting: path.FreeSpec executes contexts anyway")
        |    }
        |  }
        |}
      """.stripMargin)

    runTestByLocation(5, 15, "FreeSpecPathTest.scala",
      checkConfigAndSettings(_, "FreeSpecPathTest", "A FreeSpecTest should be able to run single test"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FreeSpecPathTest", "A FreeSpecTest", "should be able to run single test") &&
          checkResultTreeDoesNotHaveNodes(root, "should not run tests that are not selected"),
      debug = true
    )
  }
}
