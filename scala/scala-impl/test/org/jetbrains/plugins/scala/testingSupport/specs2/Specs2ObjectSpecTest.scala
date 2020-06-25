package org.jetbrains.plugins.scala
package testingSupport.specs2

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
abstract class Specs2ObjectSpecTest extends Specs2TestCase {

  override def debugProcessOutput: Boolean = true

  addSourceFile("SpecObject.scala",
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
  def testSpecObject(): Unit = {
    runTestByLocation2(5, 8, "SpecObject.scala",
      assertConfigAndSettings(_, "SpecObject", "run alone"),
      root => {
        assertResultTreeHasExactNamedPath(root, Seq("[root]", "SpecObject", "single test in SpecObject should", "run alone"))
        assertResultTreeDoesNotHaveNodes(root, "ignore other test")
      }
    )

  }
}
