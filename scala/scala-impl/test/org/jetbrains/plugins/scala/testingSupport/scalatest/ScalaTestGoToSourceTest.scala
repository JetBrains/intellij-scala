package org.jetbrains.plugins.scala.testingSupport.scalatest

trait ScalaTestGoToSourceTest extends ScalaTestTestCase {

  val goToSourceClassName = "GoToSourceTest"
  val goToSourceFileName = goToSourceClassName + ".scala"
  val goToSourceTemplateClassName = "GoToSourceTestTemplate"

  addSourceFile(goToSourceFileName,
    s"""import org.scalatest._
       |
       |class $goToSourceClassName extends $goToSourceTemplateClassName {
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
       |""".stripMargin
  )

  addSourceFile(goToSourceTemplateClassName + ".scala",
    s"""import org.scalatest._
       |
       |trait $goToSourceTemplateClassName extends FlatSpec with GivenWhenThen {
       |  "Successful in template" should "run fine" in {
       |  }
       |}
       |""".stripMargin
  )

  def getSuccessfulTestPath: TestNodePath
  def getPendingTestPath: TestNodePath
  def getIgnoredTestPath: TestNodePath
  def getFailedTestPath: TestNodePath
  def getTemplateTestPath: TestNodePath

  def getSuccessfulLocationLine: Int
  def getPendingLocationLine: Int
  def getIgnoredLocationLine: Int
  def getFailedLocationLine: Int
  def getTemplateLocationLine: Int

  def testGoToSuccessfulLocation(): Unit =
    runGoToSourceTest(
      loc(goToSourceFileName, 3, 5),
      AssertConfigAndSettings(goToSourceClassName, "Successful test should run fine"),
      getSuccessfulTestPath,
      getSuccessfulLocationLine
    )

  def testGoToPendingLocation(): Unit =
    runGoToSourceTest(
      loc(goToSourceFileName, 6, 5),
      AssertConfigAndSettings(goToSourceClassName, "pending test should be pending"),
      getPendingTestPath,
      getPendingLocationLine
    )

  //since finders API ignored ignored tests and provides neighbours for the same scope instead of ignored test poitned to
  //we run all the tests
  def testGoToIgnoredLocation(): Unit =
    runGoToSourceTest(
      loc(goToSourceFileName, 2, 5),
      AssertConfigAndSettings(goToSourceClassName),
      //notice that runConfig test name and testTree test name differ by !!! IGNORED !!! suffix
      getIgnoredTestPath,
      getIgnoredLocationLine
    )

  def testGoToFailedTest(): Unit =
    runGoToSourceTest(
      loc(goToSourceFileName, 13, 5),
      AssertConfigAndSettings(goToSourceClassName, "failed test should fail"),
      getFailedTestPath,
      getFailedLocationLine
    )

  def testGoToTemplateTest(): Unit =
    runGoToSourceTest(
      loc(goToSourceFileName, 2, 5),
      AssertConfigAndSettings(goToSourceClassName),
      getTemplateTestPath,
      getTemplateLocationLine,
      Some(goToSourceTemplateClassName + ".scala")
    )
}
