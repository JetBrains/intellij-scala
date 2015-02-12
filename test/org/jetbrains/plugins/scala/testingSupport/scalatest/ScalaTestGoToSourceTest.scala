package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 17.01.2015.
 */
trait ScalaTestGoToSourceTest extends IntegrationTest {

  def getSuccessfulTestPath: List[String]
  def getPendingTestPath: List[String]
  def getIgnoredTestPath: List[String]
  def getFailedTestPath: List[String]
  
  def getSuccessfulLocationLine: Int
  def getPendingLocationLine: Int
  def getIgnoredLocationLine: Int
  def getFailedLocationLine: Int

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
            |   fail
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
      getSuccessfulTestPath, getSuccessfulLocationLine)
  }

  def testGoToPendingLocation(): Unit = {
    val testName = "PendingGoToLocationTest"
    addGoToSourceTest(testName)

    runGoToSourceTest(6, 5, testName + ".scala",
      checkConfigAndSettings(_, testName, "pending test should be pending"),
      getPendingTestPath, getPendingLocationLine)
  }

  def testGoToIgnoredLocation(): Unit = {
    val testName = "IgnoredGoToLocationTest"
    addGoToSourceTest(testName)

    //since finders API ignored ignored tests and provides neighbours for the same scope instead of ignored test poitned to
    //we run all the tests
    runGoToSourceTest(2, 5, testName + ".scala",
      checkConfigAndSettings(_, testName),
      //notice that runConfig test name and testTree test name differ by !!! IGNORED !!! suffix
      getIgnoredTestPath, getIgnoredLocationLine)
  }

  def testGoToFailedTest(): Unit = {
    val testName = "FailedGoToLocationTest"
    addGoToSourceTest(testName)

    runGoToSourceTest(13, 5, testName + ".scala",
      checkConfigAndSettings(_, testName, "failed test should fail"),
      getFailedTestPath, getFailedLocationLine)
  }
}
