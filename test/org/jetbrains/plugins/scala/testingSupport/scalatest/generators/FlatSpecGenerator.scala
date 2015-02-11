package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 10.02.2015.
 */
trait FlatSpecGenerator extends IntegrationTest {
  def addFlatSpec() {
    addFileToProject("FlatSpecTest.scala",
      """
        |import org.scalatest._
        |
        |class FlatSpecTest extends FlatSpec with GivenWhenThen {
        | "A FlatSpecTest" should "be able to run single test" in {
        |   Given("an empty test case")
        |   val resultToPrint = ">>TEST: OK<<"
        |
        |   When("the result line is printed")
        |   print(resultToPrint)
        |
        |   Then("nothing happens in the test")
        |
        |   info("and that's quite right")
        | }
        |
        | it should "not run other tests" in {
        |   print(">>TEST: FAILED<<")
        | }
        |}
      """.stripMargin.trim()
    )
  }

  def addBehaviorFlatSpec() = {
    addFileToProject("BehaviorFlatSpec.scala",
    """
      |import org.scalatest._
      |
      |class BehaviorFlatSpec extends FlatSpec with GivenWhenThen {
      |  behavior of "FlatSpec"
      |
      |  it should "run scopes" in {
      |
      |  }
      |
      |  it should "do other stuff" in {
      |
      |  }
      |}
    """.stripMargin
    )
  }
}
