package org.jetbrains.plugins.scala
package testingSupport.specs2

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
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
      |      print(">>TEST: OK<<")
      |      1 mustEqual 1
      |    }
      |
      |    "run exclamation test" ! { success }
      |
      |    "run greater test" >> { success }
      |
      |    "ignore other test" in {
      |      print(">>TEST: FAILED<<")
      |      1 mustEqual 1
      |    }
      |  }
      |}
    """.stripMargin
  )

  def testSpecification(): Unit = {
    runTestByLocation2(5, 10, specsTestFileName,
      assertConfigAndSettings(_, specsTestClassName, "run single test"),
      root => {
        assertResultTreeHasExactNamedPath(root, Seq("[root]", specsTestClassName, "The 'SpecificationTest' should", "run single test"))
        assertResultTreeDoesNotHaveNodes(root, "ignore other test", "run greater test", "run exclamation test")
      }
    )

    runTestByLocation2(10, 35, specsTestFileName,
      assertConfigAndSettings(_, specsTestClassName, "run exclamation test"),
      root => {
        assertResultTreeHasExactNamedPath(root, Seq("[root]", specsTestClassName, "The 'SpecificationTest' should", "run exclamation test"))
        assertResultTreeDoesNotHaveNodes(root, "ignore other test", "run single test", "run greater test")
      })

    runTestByLocation2(12, 10, specsTestFileName,
      assertConfigAndSettings(_, specsTestClassName, "run greater test"),
      root => {
        assertResultTreeHasExactNamedPath(root, Seq("[root]", specsTestClassName, "The 'SpecificationTest' should", "run greater test"))
        assertResultTreeDoesNotHaveNodes(root, "ignore other test", "run single test", "run exclamation test")
      })
  }
}
