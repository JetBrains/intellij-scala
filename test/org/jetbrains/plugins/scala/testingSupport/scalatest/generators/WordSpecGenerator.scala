package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 10.02.2015.
 */
trait WordSpecGenerator extends IntegrationTest {
  def addWordSpec() {
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
  }
}
