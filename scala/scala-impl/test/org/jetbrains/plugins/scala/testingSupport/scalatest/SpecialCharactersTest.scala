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
  val classTestTreePath1 = List("[root]", className1, "test", "should work")

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

  def testComma(): Unit =
    runTestByLocation2(3, 3, className + ".scala",
      assertConfigAndSettings(_, className, "Comma , test should contain , comma"),
      root => assertResultTreeHasExactNamedPath(root, commaTestPath)
    )

  def testExclamation(): Unit =
    runTestByLocation2(6, 3, className + ".scala",
      assertConfigAndSettings(_, className, "! test should contain !"),
      root => assertResultTreeHasExactNamedPath(root, exclamationTestPath)
    )

  def testTick(): Unit =
    runTestByLocation2(9, 3, className + ".scala",
      assertConfigAndSettings(_, className, "tick ' test should contain '"),
      root => assertResultTreeHasExactNamedPath(root, tickTestPath)
    )

  def testTilde(): Unit =
    runTestByLocation2(15, 3, className + ".scala",
      assertConfigAndSettings(_, className, "tilde ~ test should contain ~"),
      root => assertResultTreeHasExactNamedPath(root, tildeTestPath)
    )

  def testBacktick(): Unit =
    runTestByLocation2(12, 3, className + ".scala",
      assertConfigAndSettings(_, className, "backtick ` test should contain `"),
      root => assertResultTreeHasExactNamedPath(root, backtickTestPath)
    )

  def testClassInPackageWithReservedKeywordInName(): Unit =
    runTestByLocation2(6, 10, classFilePath1,
      assertConfigAndSettings(_, classFullName1, "test should work"),
      root => assertResultTreeHasExactNamedPath(root, classTestTreePath1)
    )
}
