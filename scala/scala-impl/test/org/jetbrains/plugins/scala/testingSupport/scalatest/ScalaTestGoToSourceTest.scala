package org.jetbrains.plugins.scala.testingSupport.scalatest

trait ScalaTestGoToSourceTest extends ScalaTestTestCase {

  val goToSourceClassName = "GoToSourceTest"

  addSourceFile(goToSourceClassName + ".scala",
    s"""import org.scalatest._
       |
       |class $goToSourceClassName extends FlatSpec with GivenWhenThen {
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
       |}""".stripMargin
  )


  def getSuccessfulTestPath: List[String]

  def getPendingTestPath: List[String]

  def getIgnoredTestPath: List[String]

  def getFailedTestPath: List[String]

  def getSuccessfulLocationLine: Int

  def getPendingLocationLine: Int

  def getIgnoredLocationLine: Int

  def getFailedLocationLine: Int

  def testGoToSuccessfulLocation(): Unit = {
    runGoToSourceTest(3, 5, goToSourceClassName + ".scala",
      checkConfigAndSettings(_, goToSourceClassName, "Successful test should run fine"),
      getSuccessfulTestPath, getSuccessfulLocationLine)
  }

  def testGoToPendingLocation(): Unit = {
    runGoToSourceTest(6, 5, goToSourceClassName + ".scala",
      checkConfigAndSettings(_, goToSourceClassName, "pending test should be pending"),
      getPendingTestPath, getPendingLocationLine)
  }

  def testGoToIgnoredLocation(): Unit = {
    //since finders API ignored ignored tests and provides neighbours for the same scope instead of ignored test poitned to
    //we run all the tests
    runGoToSourceTest(2, 5, goToSourceClassName + ".scala",
      checkConfigAndSettings(_, goToSourceClassName),
      //notice that runConfig test name and testTree test name differ by !!! IGNORED !!! suffix
      getIgnoredTestPath, getIgnoredLocationLine)
  }

  def testGoToFailedTest(): Unit = {
    runGoToSourceTest(13, 5, goToSourceClassName + ".scala",
      checkConfigAndSettings(_, goToSourceClassName, "failed test should fail"),
      getFailedTestPath, getFailedLocationLine)
  }
}
