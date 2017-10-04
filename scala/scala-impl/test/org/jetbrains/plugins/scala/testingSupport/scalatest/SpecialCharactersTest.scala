package org.jetbrains.plugins.scala.testingSupport.scalatest

/**
  * @author Roman.Shein
  * @since 18.01.2015.
  */
trait SpecialCharactersTest extends ScalaTestTestCase {

  val className = "SpecialCharachersTest"

  def commaTestPath = List("[root]", className, "Comma , test", "should contain , comma")
  def exclamationTestPath = List("[root]", className, "! test", "should contain !")
  def tickTestPath = List("[root]", className, "tick ' test", "should contain '")
  def tildeTestPath = List("[root]", className, "tilde ~ test", "should contain ~")
  def backtickTestPath = List("[root]", className, "backtick ` test", "should contain `")

  addSourceFile(s"$className.scala",
    s"""|import org.scalatest._
        |
        |class $className extends FlatSpec with GivenWhenThen {
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
        |}""".stripMargin
  )

  def testComma() {
    runTestByLocation(3, 3, className + ".scala",
      checkConfigAndSettings(_, className, "Comma , test should contain , comma"),
      root => checkResultTreeHasExactNamedPath(root, commaTestPath: _*)
    )
  }

  def testExclamation() {
    runTestByLocation(6, 3, className + ".scala",
      checkConfigAndSettings(_, className, "! test should contain !"),
      root => checkResultTreeHasExactNamedPath(root, exclamationTestPath: _*)
    )
  }

  def testTick() {
    runTestByLocation(9, 3, className + ".scala",
      checkConfigAndSettings(_, className, "tick ' test should contain '"),
      root => checkResultTreeHasExactNamedPath(root, tickTestPath: _*)
    )
  }

  def testTilde() {
    runTestByLocation(15, 3, className + ".scala",
      checkConfigAndSettings(_, className, "tilde ~ test should contain ~"),
      root => checkResultTreeHasExactNamedPath(root, tildeTestPath: _*)
    )
  }

  def testBacktick() {
    runTestByLocation(12, 3, className + ".scala",
      checkConfigAndSettings(_, className, "backtick ` test should contain `"),
      root => checkResultTreeHasExactNamedPath(root, backtickTestPath: _*)
    )
  }

}
