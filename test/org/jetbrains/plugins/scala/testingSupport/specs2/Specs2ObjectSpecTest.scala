package org.jetbrains.plugins.scala
package testingSupport.specs2

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
abstract class Specs2ObjectSpecTest extends Specs2TestCase {
  def testSpecObject() {
    addFileToProject("SpecObject.scala",
    """
      |import org.specs2.mutable.Specification
      |
      |object SpecObject extends Specification {
      |  "single test in SpecObject" should {
      |    "run alone" in {
      |      print(">>TEST: OK<<")
      |      true must_== true
      |    }
      |
      |    "ignore other test" in {
      |      print(">>TEST: FAILED<<")
      |      true must_== true
      |    }
      |  }
      |}
    """.stripMargin)

    runTestByLocation(5, 8, "SpecObject.scala",
      checkConfigAndSettings(_, "SpecObject", "run alone"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "SpecObject", "single test in SpecObject should", "run alone") &&
          checkResultTreeDoesNotHaveNodes(root, "ignore other test"),
      debug = true
    )

  }
}
