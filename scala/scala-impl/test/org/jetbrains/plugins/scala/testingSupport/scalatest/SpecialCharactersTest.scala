package org.jetbrains.plugins.scala.testingSupport.scalatest

trait SpecialCharactersTest extends ScalaTestTestCase {

  val className = "SpecialCharachersTest"
  val fileName = className + ".scala"

  def commaTestPath = TestNodePath("[root]", className, "Comma , test", "should contain , comma")
  def exclamationTestPath = TestNodePath("[root]", className, "! test", "should contain !")
  def tickTestPath = TestNodePath("[root]", className, "tick ' test", "should contain '")
  def tildeTestPath = TestNodePath("[root]", className, "tilde ~ test", "should contain ~")
  def backtickTestPath = TestNodePath("[root]", className, "backtick ` test", "should contain `")

  val className1 = "ClassInPackageWithReservedKeywordsTest"
  val packageName1 = "myPackage.type.implicit"
  val classFullName1 = packageName1 + s".$className1"
  val classFilePath1 = packageName1.replace(".", "/") + s"/$className1.scala"
  val classTestTreePath1 = TestNodePath("[root]", className1, "test", "should work")

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
    runTestByLocation(loc(fileName, 3, 3),
      assertConfigAndSettings(_, className, "Comma , test should contain , comma"),
      root => assertResultTreeHasExactNamedPath(root, commaTestPath)
    )

  def testExclamation(): Unit =
    runTestByLocation(loc(fileName, 6, 3),
      assertConfigAndSettings(_, className, "! test should contain !"),
      root => assertResultTreeHasExactNamedPath(root, exclamationTestPath)
    )

  def testTick(): Unit =
    runTestByLocation(loc(fileName, 9, 3),
      assertConfigAndSettings(_, className, "tick ' test should contain '"),
      root => assertResultTreeHasExactNamedPath(root, tickTestPath)
    )

  def testTilde(): Unit =
    runTestByLocation(loc(fileName, 15, 3),
      assertConfigAndSettings(_, className, "tilde ~ test should contain ~"),
      root => assertResultTreeHasExactNamedPath(root, tildeTestPath)
    )

  def testBacktick(): Unit =
    runTestByLocation(loc(fileName, 12, 3),
      assertConfigAndSettings(_, className, "backtick ` test should contain `"),
      root => assertResultTreeHasExactNamedPath(root, backtickTestPath)
    )

  def testClassInPackageWithReservedKeywordInName(): Unit =
    runTestByLocation(loc(classFilePath1, 6, 10),
      assertConfigAndSettings(_, classFullName1, "test should work"),
      root => assertResultTreeHasExactNamedPath(root, classTestTreePath1)
    )
}
