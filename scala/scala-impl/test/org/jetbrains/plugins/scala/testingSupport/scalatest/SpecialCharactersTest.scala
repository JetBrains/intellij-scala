package org.jetbrains.plugins.scala.testingSupport.scalatest

trait SpecialCharactersTest extends ScalaTestTestCase {

  val className = "SpecialCharachersTest"

  def commaTestPath = List("[root]", className, "Comma , test", "should contain , comma")
  def exclamationTestPath = List("[root]", className, "! test", "should contain !")
  def tickTestPath = List("[root]", className, "tick ' test", "should contain '")
  def tildeTestPath = List("[root]", className, "tilde ~ test", "should contain ~")
  def backtickTestPath = List("[root]", className, "backtick ` test", "should contain `")

  val className1 = "ClassInPackageWithReservedKeywordsTest"
  val packageName1 = "myPackage.type.implicit"
  val classFullName1 = packageName1 + s".$className1"
  val classFilePath1 = packageName1.replace(".", "/") + s"/$className1.scala"
  def classTestTreePath1 = List("[root]", className1, "test", "should work")

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

  addSourceFile(classFilePath1,
    s"""|package myPackage.`type`.`implicit`
        |
        |import org.scalatest._
        |
        |class $className1 extends FlatSpec {
        |
        |  "test" should "work" in {}
        |}""".stripMargin
  )

  def testComma(): Unit = {
    runTestByLocation(3, 3, className + ".scala",
      checkConfigAndSettings(_, className, "Comma , test should contain , comma"),
      root => checkResultTreeHasExactNamedPath(root, commaTestPath: _*)
    )
  }

  def testExclamation(): Unit = {
    runTestByLocation(6, 3, className + ".scala",
      checkConfigAndSettings(_, className, "! test should contain !"),
      root => checkResultTreeHasExactNamedPath(root, exclamationTestPath: _*)
    )
  }

  def testTick(): Unit = {
    runTestByLocation(9, 3, className + ".scala",
      checkConfigAndSettings(_, className, "tick ' test should contain '"),
      root => checkResultTreeHasExactNamedPath(root, tickTestPath: _*)
    )
  }

  def testTilde(): Unit = {
    runTestByLocation(15, 3, className + ".scala",
      checkConfigAndSettings(_, className, "tilde ~ test should contain ~"),
      root => checkResultTreeHasExactNamedPath(root, tildeTestPath: _*)
    )
  }

  def testBacktick(): Unit = {
    runTestByLocation(12, 3, className + ".scala",
      checkConfigAndSettings(_, className, "backtick ` test should contain `"),
      root => checkResultTreeHasExactNamedPath(root, backtickTestPath: _*)
    )
  }

  def testClassInPackageWithReservedKeywordInName(): Unit = {
    runTestByLocation(6, 10, classFilePath1,
      checkConfigAndSettings(_, classFullName1, "test should work"),
      root => checkResultTreeHasExactNamedPath(root, classTestTreePath1: _*)
    )
  }
}
