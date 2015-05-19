package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 10.02.2015.
 */
trait FreeSpecPathGenerator extends IntegrationTest {
  def addPathFreeSpec() {
    addFileToProject("FreeSpecPathTest.scala",
      """
        |import org.scalatest._
        |
        |class FreeSpecPathTest extends path.FreeSpec {
        |  "A FreeSpecTest" - {
        |    "should be able to run single test" in {
        |      print(">>TEST: OK<<")
        |    }
        |
        |    "should not run tests that are not selected" in {
        |      print("nothing interesting: path.FreeSpec executes contexts anyway")
        |    }
        |  }
        |}
      """.stripMargin.trim())
  }
}
