package org.jetbrains.plugins.scala.testingSupport.scalatest

trait IgnoredSpecTest extends ScalaTestTestCase {
  def ignoredTestPath: List[String] = List("[root]", "IgnoredTestSpec", "An IgnoredTestSpec", "should be ignored and have proper suffix !!! IGNORED !!!")
  def succeededTestPath: List[String] = List("[root]", "IgnoredTestSpec", "An IgnoredTestSpec", "should run tests")

  addSourceFile("IgnoredTest.scala",
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

  def testIgnoredTest(): Unit = {
    runTestByLocation(2, 7, "IgnoredTest.scala",
      checkConfigAndSettings(_, "IgnoredTestSpec"),
      root => checkResultTreeHasExactNamedPath(root, succeededTestPath: _*) &&
        checkResultTreeHasExactNamedPath(root, ignoredTestPath: _*)
    )
  }
}
