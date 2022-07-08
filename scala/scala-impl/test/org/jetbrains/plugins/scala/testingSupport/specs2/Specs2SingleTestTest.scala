package org.jetbrains.plugins.scala.testingSupport.specs2

abstract class Specs2SingleTestTest extends Specs2TestCase {
  protected val specsTestFileName = "SpecificationTest.scala"
  protected val specsTestClassName = "SpecificationTest"

  addSourceFile(specsTestFileName,
    s"""
      |import org.specs2.mutable.Specification
      |
      |class $specsTestClassName extends Specification {
      |  "The 'SpecificationTest'" should {
      |    "run single test" in {
      |      print("$TestOutputPrefix OK $TestOutputSuffix")
      |      1 mustEqual 1
      |    }
      |
      |    "run exclamation test" ! { success }
      |
      |    "run greater test" >> { success }
      |
      |    "ignore other test" in {
      |      print("$TestOutputPrefix FAILED $TestOutputSuffix")
      |      1 mustEqual 1
      |    }
      |  }
      |}
    """.stripMargin
  )

  def testSpecification(): Unit = {
    runTestByLocation(loc(specsTestFileName, 5, 10),
      assertConfigAndSettings(_, specsTestClassName, "run single test"),
      root => {
        assertResultTreeHasExactNamedPath(root, TestNodePath("[root]", specsTestClassName, "The 'SpecificationTest' should", "run single test"))
        assertResultTreeDoesNotHaveNodes(root, "ignore other test", "run greater test", "run exclamation test")
      }
    )

    runTestByLocation(loc(specsTestFileName, 10, 35),
      assertConfigAndSettings(_, specsTestClassName, "run exclamation test"),
      root => {
        assertResultTreeHasExactNamedPath(root, TestNodePath("[root]", specsTestClassName, "The 'SpecificationTest' should", "run exclamation test"))
        assertResultTreeDoesNotHaveNodes(root, "ignore other test", "run single test", "run greater test")
      })

    runTestByLocation(loc(specsTestFileName, 12, 10),
      assertConfigAndSettings(_, specsTestClassName, "run greater test"),
      root => {
        assertResultTreeHasExactNamedPath(root, TestNodePath("[root]", specsTestClassName, "The 'SpecificationTest' should", "run greater test"))
        assertResultTreeDoesNotHaveNodes(root, "ignore other test", "run single test", "run exclamation test")
      })
  }
}
