package org.jetbrains.plugins.scala
package testingSupport.specs2

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
abstract class Specs2SingleTestTest extends Specs2TestCase {
  def testSpecification() {
    addFileToProject("SpecificationTest.scala",
    """
      |import org.specs2.mutable.Specification
      |
      |class SpecificationTest extends Specification {
      |  "The 'SpecificationTest'" should {
      |    "run single test" in {
      |      print(">>TEST: OK<<")
      |      1 mustEqual 1
      |    }
      |
      |    "ignore other test" in {
      |      print(">>TEST: FAILED<<")
      |      1 mustEqual 1
      |    }
      |  }
      |}
    """.stripMargin
    )

    runTestByLocation(5, 10, "SpecificationTest.scala",
      checkConfigAndSettings(_, "SpecificationTest", "run single test"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "SpecificationTest", "The 'SpecificationTest' should", "run single test") &&
          checkResultTreeDoesNotHaveNodes(root, "ignore other test"),
      debug = true
    )
  }
}
