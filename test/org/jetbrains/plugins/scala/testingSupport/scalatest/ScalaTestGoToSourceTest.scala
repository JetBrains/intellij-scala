package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 17.01.2015.
 */
trait ScalaTestGoToSourceTest extends IntegrationTest {

  private def addGoToSourceTest(testName: String) =
    addFileToProject(testName + ".scala",
      "import org.scalatest._\n\n" +
          "class " + testName + " extends FlatSpec with GivenWhenThen {" +
          """
            | "Successful test" should "run fine" in {
            | }
            |
            | "pending test" should "be pending" in {
            |   pending
            | }
            |
            | ignore should "be ignored" in {
            | }
            |
            | "failed test" should "fail" in {
            |
            | }
            |}
            |
          """.stripMargin
    )

  def testGoToSuccessfulLocation(): Unit = {
    val testName = "SuccessfulGoToLocationTest"
    addGoToSourceTest(testName)

    runGoToSourceTest(3, 5, testName + ".scala",
      checkConfigAndSettings(_, testName, "Successful test should run fine"),
      List("[root]", testName, "Successful test", "should run fine"), 3)
  }

  def testGoToPendingLocation(): Unit = {
    val testName = "PendingGoToLocationTest"
    addGoToSourceTest(testName)

    runGoToSourceTest(6, 5, testName + ".scala",
      checkConfigAndSettings(_, testName, "pending test should be pending"),
      List("[root]", testName, "pending test", "should be pending"), 6)
  }

  def testGoToIgnoredLocation(): Unit = {
    val testName = "IgnoredGoToLocationTest"
    addGoToSourceTest(testName)

    runGoToSourceTest(10, 5, testName + ".scala",
      checkConfigAndSettings(_, testName, "pending test should be ignored"),
      //notice that runConfig test name and testTree test name differ by !!! IGNORED !!! suffix
      List("[root]", testName, "pending test", "should be ignored !!! IGNORED !!!"), 10)
  }

  def testGoToFailedTest(): Unit = {
    val testName = "FailedGoToLocationTest"
    addGoToSourceTest(testName)

    runGoToSourceTest(13, 5, testName + ".scala",
      checkConfigAndSettings(_, testName, "failed test should fail"),
      List("[root]", testName, "failed test", "should fail"), 13)
  }
}
