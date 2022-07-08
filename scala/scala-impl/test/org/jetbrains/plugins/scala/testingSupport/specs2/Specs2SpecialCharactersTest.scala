package org.jetbrains.plugins.scala.testingSupport.specs2

abstract class Specs2SpecialCharactersTest extends Specs2TestCase {
  val testName = "Specs2SpecialCharactersTest"
  val fileName = testName + ".scala"

  addSourceFile(s"$testName.scala",
    s"""import org.specs2.mutable.Specification
       |
       |class $testName extends Specification {
       | "Special characters test" should {
       |
       |   "Comma , test" in {
       |     success
       |   }
       |
       |   "! test" in {
       |     success
       |   }
       |
       |   "tick ' test" in {
       |     success
       |   }
       |
       |   "backtick ` test" in {
       |     success
       |   }
       |
       |   "tilde ~ test" in {
       |     success
       |   }
       | }
       |}""".stripMargin
  )

  def testComma(): Unit = {
    runTestByLocation(loc(fileName, 5, 5),
      assertConfigAndSettings(_, testName, "Comma , test"),
      root => assertResultTreeHasExactNamedPath(root, TestNodePath("[root]", testName, "Special characters test should", "Comma , test"))
    )
  }

  def testExclamation(): Unit = {
    runTestByLocation(loc(fileName, 9, 5),
      assertConfigAndSettings(_, testName, "! test"),
      root => assertResultTreeHasExactNamedPath(root, TestNodePath("[root]", testName, "Special characters test should", "! test")))
  }

  def testTick(): Unit = {
    runTestByLocation(loc(fileName, 13, 5),
      assertConfigAndSettings(_, testName, "tick ' test"),
      root => assertResultTreeHasExactNamedPath(root, TestNodePath("[root]", testName, "Special characters test should", "tick ' test")))
  }

  def testBacktick(): Unit = {
    runTestByLocation(loc(fileName, 17, 5),
      assertConfigAndSettings(_, testName, "backtick ` test"),
      root => assertResultTreeHasExactNamedPath(root, TestNodePath("[root]", testName, "Special characters test should", "backtick ` test")))
  }

  def testTilde(): Unit = {
    runTestByLocation(loc(fileName, 21, 5),
      assertConfigAndSettings(_, testName, "tilde ~ test"),
      root => assertResultTreeHasExactNamedPath(root, TestNodePath("[root]", testName, "Special characters test should", "tilde ~ test")))
  }
}
