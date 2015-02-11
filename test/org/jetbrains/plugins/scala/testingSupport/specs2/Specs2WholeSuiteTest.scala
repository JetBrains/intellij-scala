package org.jetbrains.plugins.scala.testingSupport.specs2

/**
 * @author Roman.Shein
 * @since 11.02.2015.
 */
abstract class Specs2WholeSuiteTest extends Specs2TestCase {
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

    runTestByLocation(3, 14, "SpecificationTest.scala",
      checkConfigAndSettings(_, "SpecificationTest"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "SpecificationTest", "The 'SpecificationTest' should", "run single test") &&
          checkResultTreeHasExactNamedPath(root, "[root]", "SpecificationTest", "The 'SpecificationTest' should", "ignore other test"),
      debug = true
    )
  }
}
