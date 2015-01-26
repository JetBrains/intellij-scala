package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.{TestByLocationRunner, IntegrationTest}

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait FlatSpecSingleTestTest extends IntegrationTest {
  def testFlatSpec() {
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

    runTestByLocation(7, 1, "FlatSpecTest.scala",
      checkConfigAndSettings(_, "FlatSpecTest", "A FlatSpecTest should be able to run single test"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FlatSpecTest", "A FlatSpecTest", "should be able to run single test") &&
          checkResultTreeDoesNotHaveNodes(root, "should not run other tests"),
      debug = true
    )
  }
}
