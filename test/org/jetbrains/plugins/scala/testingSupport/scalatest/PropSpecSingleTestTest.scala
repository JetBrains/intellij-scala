package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait PropSpecSingleTestTest extends IntegrationTest {
  def testPropSpec() {
    addFileToProject("PropSpecTest.scala",
      """
        |import org.scalatest._
        |
        |class PropSpecTest extends PropSpec {
        |
        |  property("Single tests should run") {
        |    print(">>TEST: OK<<")
        |  }
        |
        |  property("other test should not run") {
        |    print(">>TEST: FAILED<<")
        |  }
        |}
      """.stripMargin
    )

    runTestByLocation(5, 5, "PropSpecTest.scala",
      checkConfigAndSettings(_, "PropSpecTest", "Single tests should run"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "PropSpecTest", "Single tests should run") &&
          checkResultTreeDoesNotHaveNodes(root, "other tests should not run"),
      debug = true
    )
  }
}
