package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 22.01.2015.
 */
trait IgnoredSpecTest extends IntegrationTest {
  val ignoredTestPath: List[String] = List("[root]", "IgnoredTestSpec", "An IgnoredTestSpec", "should be ignored and have proper suffix !!! IGNORED !!!")
  val succeededTestPath: List[String] = List("[root]", "IgnoredTestSpec", "An IgnoredTestSpec", "should run tests")

  def testIgnoredTest(): Unit = {
    addFileToProject("IgnoredTest.scala",
      """
        |import org.scalatest._
        |
        |class IgnoredTestSpec extends FlatSpec with GivenWhenThen {
        | "An IgnoredTestSpec" should "run tests" in {
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
        | ignore should "be ignored and have proper suffix" in {
        |   print(">>TEST: FAILED<<")
        | }
        |}
      """.stripMargin.trim()
    )

    runTestByLocation(2, 7, "IgnoredTest.scala",
      checkConfigAndSettings(_, "IgnoredTestSpec"),
      root => checkResultTreeHasExactNamedPath(root, succeededTestPath:_*) &&
          checkResultTreeHasExactNamedPath(root, ignoredTestPath:_*)
    )
  }
}
