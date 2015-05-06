package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 10.02.2015.
 */
trait WordSpecGenerator extends IntegrationTest {
  def wordSpecClassName = "WordSpecTest"
  def wordSpecFileName = wordSpecClassName + ".scala"

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
        |
        |  "empty" should {}
        |
        |  "outer" should {
        |    "inner" in {}
        |  }
        |}
      """.stripMargin.trim()
    )
  }
}
