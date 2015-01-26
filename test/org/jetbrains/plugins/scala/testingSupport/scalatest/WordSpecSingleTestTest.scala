package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait WordSpecSingleTestTest extends IntegrationTest {
  def testWordSpec() {
    addFileToProject("WordSpecTest.scala",
      """
        |import org.scalatest._
        |
        |class WordSpecTest extends WordSpec {
        |  "WordSpecTest" should {
        |    "Run single test" in {
        |      print(">>TEST: OK<<")
        |    }
        |
        |    "ignore other tests" in {
        |      print(">>TEST: FAILED<<")
        |    }
        |  }
        |}
      """.stripMargin
    )

    runTestByLocation(5, 10, "WordSpecTest.scala",
      checkConfigAndSettings(_, "WordSpecTest", "WordSpecTest should Run single test"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "WordSpecTest", "WordSpecTest", "Run single test") &&
          checkResultTreeDoesNotHaveNodes(root, "ignore other tests"),
      debug = true
    )
  }
}
