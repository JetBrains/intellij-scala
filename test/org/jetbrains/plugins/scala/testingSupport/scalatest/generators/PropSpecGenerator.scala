package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 10.02.2015.
 */
trait PropSpecGenerator extends IntegrationTest {
  def addPropSpec() {
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
  }
}
