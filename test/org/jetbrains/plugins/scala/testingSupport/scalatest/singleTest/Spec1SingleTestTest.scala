package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 22.01.2015.
 */
trait Spec1SingleTestTest extends IntegrationTest {
  //TODO: stop ignoring it once support for Spec is fixed
  def testSpec() {
    addFileToProject("Spec.scala",
      """
        |import org.scalatest._
        |
        |class SpecTest extends Spec {
        |  describe("SpecTest") {
        |    it ("should launch single test") {
        |      print(">>TEST: OK<<")
        |    }
        |
        |    it ("should not launch other tests") {
        |      print(">>TEST: FAILED<<")
        |    }
        |  }
        |}
      """.stripMargin
    )

    runTestByLocation(6, 9, "Spec.scala",
      checkConfigAndSettings(_, "SpecTest", "SpecTest should launch single test"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "SpecTest", "SpecTest", "should launch single test") &&
          checkResultTreeDoesNotHaveNodes(root, "should not launch other tests"),
      debug = true
    )
  }
}
