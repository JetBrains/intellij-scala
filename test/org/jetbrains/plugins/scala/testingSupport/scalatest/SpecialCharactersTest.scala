package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 18.01.2015.
 */
trait SpecialCharactersTest extends IntegrationTest {

  val commaTestPath = List("[root]", "TestComma", "Comma , test", "should contain , comma")
  val exclamationTestPath = List("[root]", "TestExclamation", "! test", "should contain !")
  val tickTestPath = List("[root]", "TestTick", "tick ' test", "should contain '")
  val tildeTestPath = List("[root]", "TestTilde", "tilde ~ test", "should contain ~")
  val backtickTestPath = List("[root]", "TestBacktick", "backtick ` test", "should contain `")

  private def addSpecialCharactersTest(testName: String) =
    addFileToProject(testName + ".scala",
      "import org.scalatest._\n\n" +
          "class " + testName + " extends FlatSpec with GivenWhenThen {" +
          """
            | "Comma , test" should "contain , comma" in {
            | }
            |
            | "! test" should "contain !" in {
            | }
            |
            | "tick ' test" should "contain '" in {
            | }
            |
            | "backtick ` test" should "contain `" in {
            | }
            |
            | "tilde ~ test" should "contain ~" in {
            | }
            |}
            |
          """.stripMargin
    )

  def testComma() {
    val testName = "TestComma"
    addSpecialCharactersTest(testName)

    runTestByLocation(3, 3, testName + ".scala",
      checkConfigAndSettings(_, testName, "Comma , test should contain , comma"),
      root => checkResultTreeHasExactNamedPath(root, commaTestPath:_*)
    )
  }

  def testExclamation() {
    val testName = "TestExclamation"
    addSpecialCharactersTest(testName)

    runTestByLocation(6, 3, testName + ".scala",
      checkConfigAndSettings(_, testName, "! test should contain !"),
      root => checkResultTreeHasExactNamedPath(root, exclamationTestPath:_*)
    )
  }

  def testTick() {
    val testName = "TestTick"
    addSpecialCharactersTest(testName)
    runTestByLocation(9, 3, testName + ".scala",
      checkConfigAndSettings(_, testName, "tick ' test should contain '"),
      root => checkResultTreeHasExactNamedPath(root, tickTestPath:_*)
    )
  }

  def testTilde() {
    val testName = "TestTilde"
    addSpecialCharactersTest(testName)
    runTestByLocation(15, 3, testName + ".scala",
      checkConfigAndSettings(_, testName, "tilde ~ test should contain ~"),
      root => checkResultTreeHasExactNamedPath(root, tildeTestPath:_*)
    )
  }

  def testBacktick() {
    val testName = "TestBacktick"
    addSpecialCharactersTest(testName)
    runTestByLocation(12, 3, testName + ".scala",
      checkConfigAndSettings(_, testName, "backtick ` test should contain `"),
      root => checkResultTreeHasExactNamedPath(root, backtickTestPath:_*)
    )
  }

}
