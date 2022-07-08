package org.jetbrains.plugins.scala.testingSupport.specs2

abstract class Specs2RegExpTestNameTest extends Specs2TestCase {
  protected val regExpClassName = "SpecsRegExpTest"
  protected val regExpFileName = regExpClassName + ".scala"

  addSourceFile(regExpFileName,
    """
      |import org.specs2.mutable.Specification
      |
      |class SpecsRegExpTest extends Specification {
      |  "The RegExpTest" should {
      |    "testtesttest" in {
      |      1 mustEqual 1
      |    }
      |
      |    "test" ! { success }
      |
      |    "testtest" >> { success }
      |  }
      |
      |  "First" should {
      |    "run" ! { success }
      |  }
      |
      |  "Second" should {
      |    "run" ! { success }
      |  }
      |}
    """.stripMargin.trim)

  def testInnerMost(): Unit =
    runTestByLocation(loc(regExpFileName, 8, 10),
      assertConfigAndSettings(_, regExpClassName, "test"),
      root => {
        assertResultTreeHasExactNamedPath(root, TestNodePath("[root]", regExpClassName, "The RegExpTest should", "test"))
        assertResultTreeDoesNotHaveNodes(root, "testtest", "testtesttest")
      }
    )

  def testMiddle(): Unit =
    runTestByLocation(loc(regExpFileName, 10, 10),
      assertConfigAndSettings(_, regExpClassName, "testtest"),
      root => {
        assertResultTreeHasExactNamedPath(root, TestNodePath("[root]", regExpClassName, "The RegExpTest should", "testtest"))
        assertResultTreeDoesNotHaveNodes(root, "test", "testtesttest")
      })

  def testOuterMost(): Unit =
    runTestByLocation(loc(regExpFileName, 4, 10),
      assertConfigAndSettings(_, regExpClassName, "testtesttest"),
      root => {
        assertResultTreeHasExactNamedPath(root, TestNodePath("[root]", regExpClassName, "The RegExpTest should", "testtesttest"))
        assertResultTreeDoesNotHaveNodes(root, "test", "testtest")
      })

  //TODO: enable the test once I find a way to run different tests with same description in specs2
  def __IGNORE_testDifferentScopes(): Unit = {
    runTestByLocation(loc(regExpFileName, 14, 10),
      assertConfigAndSettings(_, regExpClassName, "run"),
      root => {
        assertResultTreeHasExactNamedPath(root, TestNodePath("[root]", regExpClassName, "First should", "run"))
        assertResultTreeDoesNotHaveNodes(root, "Second should")
      })

    runTestByLocation(loc(regExpFileName, 18, 10),
      assertConfigAndSettings(_, regExpClassName, "run"),
      root => {
        assertResultTreeHasExactNamedPath(root, TestNodePath("[root]", regExpClassName, "Second should", "run"))
        assertResultTreeDoesNotHaveNodes(root, "First should")
      })
  }
}
