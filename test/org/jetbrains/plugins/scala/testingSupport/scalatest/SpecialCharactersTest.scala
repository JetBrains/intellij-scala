package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 18.01.2015.
 */
trait SpecialCharactersTest extends IntegrationTest {
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
      root => checkResultTreeHasExactNamedPath(root, "[root]", testName, "Comma , test", "should contain , comma")
    )
  }

  def testExclamation() {
    val testName = "TestExclamation"
    addSpecialCharactersTest(testName)

    runTestByLocation(6, 3, testName + ".scala",
      checkConfigAndSettings(_, testName, "! test should contain !"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", testName, "! test", "should contain !")
    )
  }

  def testTick() {
    val testName = "TestTick"
    addSpecialCharactersTest(testName)
    runTestByLocation(9, 3, testName + ".scala",
      checkConfigAndSettings(_, testName, "tick ' test should contain '"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "TestTick", "tick ' test", "should contain '")
    )
  }

  def testTilde() {
    val testName = "TestTilde"
    addSpecialCharactersTest(testName)
    runTestByLocation(15, 3, testName + ".scala",
      checkConfigAndSettings(_, testName, "tilde ~ test should contain ~"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", testName, "tilde ~ test", "should contain ~")
    )
  }

  def testBacktick() {
    val testName = "TestBacktick"
    addSpecialCharactersTest(testName)
    runTestByLocation(12, 3, testName + ".scala",
      checkConfigAndSettings(_, testName, "backtick ` test should contain `"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", testName, "backtick ` test", "should contain `")
    )
  }

}
