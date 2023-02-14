package org.jetbrains.plugins.scala.testingSupport.scalatest.base

trait ScalaTestGoToSourceTest extends ScalaTestTestCase {

  protected val GoToSourceClassName = "GoToSourceTest"
  protected val GoToSourceFileName = GoToSourceClassName + ".scala"
  protected val GoToSourceTemplateClassName = "GoToSourceTestTemplate"

  addSourceFile(GoToSourceFileName,
    s"""import org.scalatest._
       |
       |class $GoToSourceClassName extends $GoToSourceTemplateClassName {
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
       |   fail()
       | }
       |}
       |""".stripMargin
  )

  addSourceFile(GoToSourceTemplateClassName + ".scala",
    s"""$ImportsForFlatSpec
       |
       |trait $GoToSourceTemplateClassName extends $FlatSpecBase {
       |  "Successful in template" should "run fine" in {
       |  }
       |}
       |""".stripMargin
  )

  def testGoToSuccessfulLocation(): Unit =
    runGoToSourceTest(
      loc(GoToSourceFileName, 3, 5),
      config => assertConfigAndSettings(config, GoToSourceClassName, "Successful test should run fine"),
      TestNodePath("[root]", GoToSourceClassName, "Successful test", "should run fine"),
      sourceLine = 3
    )

  def testGoToPendingLocation(): Unit =
    runGoToSourceTest(
      loc(GoToSourceFileName, 6, 5),
      config => assertConfigAndSettings(config, GoToSourceClassName, "pending test should be pending"),
      TestNodePath("[root]", GoToSourceClassName, "pending test", "should be pending"),
      sourceLine = 6
    )

  //since finders API ignored ignored tests and provides neighbours for the same scope instead of ignored test poitned to
  //we run all the tests
  def testGoToIgnoredLocation(): Unit =
    runGoToSourceTest(
      loc(GoToSourceFileName, 2, 5),
      config => assertConfigAndSettings(config, GoToSourceClassName),
      //notice that runConfig test name and testTree test name differ by !!! IGNORED !!! suffix
      TestNodePath("[root]", GoToSourceClassName, "pending test", "should be ignored !!! IGNORED !!!"),
      sourceLine = 10
    )

  def testGoToFailedTest(): Unit =
    runGoToSourceTest(
      loc(GoToSourceFileName, 13, 5),
      config => assertConfigAndSettings(config, GoToSourceClassName, "failed test should fail"),
      TestNodePath("[root]", GoToSourceClassName, "failed test", "should fail"),
      sourceLine = 13
    )

  def testGoToTemplateTest(): Unit =
    runGoToSourceTest(
      loc(GoToSourceFileName, 2, 5),
      config => assertConfigAndSettings(config, GoToSourceClassName),
      TestNodePath("[root]", GoToSourceClassName, "Successful in template", "should run fine"),
      sourceLine = 3,
      Some(GoToSourceTemplateClassName + ".scala")
    )
}
