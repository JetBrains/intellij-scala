package org.jetbrains.plugins.scala.testingSupport.specs2

abstract class Specs2ObjectSpecTest extends Specs2TestCase {

  addSourceFile("SpecObject.scala",
    s"""
       |import org.specs2.mutable.Specification
       |
       |object SpecObject extends Specification {
       |  "single test in SpecObject" should {
       |    "run alone" in {
       |      print("$TestOutputPrefix OK $TestOutputSuffix")
       |      true must_== true
       |    }
       |
       |    "ignore other test" in {
       |      print("$TestOutputPrefix FAILED $TestOutputSuffix")
       |      true must_== true
       |    }
       |  }
       |}
    """.stripMargin)
  def testSpecObject(): Unit = {
    runTestByLocation(loc("SpecObject.scala", 5, 8),
      assertConfigAndSettings(_, "SpecObject", "run alone"),
      root => {
        assertResultTreeHasExactNamedPath(root, TestNodePath("[root]", "SpecObject", "single test in SpecObject should", "run alone"))
        assertResultTreeDoesNotHaveNodes(root, "ignore other test")
      }
    )

  }
}
