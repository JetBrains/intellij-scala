package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 28.01.2015.
 */
trait NestedSameNamesTest extends IntegrationTest {
  val testPath = List("[root]", "FunSpecTest", "FunSpecTest", "FunSpecTest", "FunSpecTest")

  def testNestedSameNames(): Unit = {
    addFileToProject("FunSpecTest.scala",
      """
        |import org.scalatest._
        |
        |class FunSpecTest extends FunSpec {
        |  describe("FunSpecTest") {
        |    describe("FunSpecTest") {
        |      it ("FunSpecTest") {
        |        print(">>TEST: OK<<")
        |      }
        |    }
        |  }
        |}
      """.stripMargin
    )

    runTestByLocation(6, 12, "FunSpecTest.scala",
      checkConfigAndSettings(_, "FunSpecTest", "FunSpecTest FunSpecTest FunSpecTest"),
      root => checkResultTreeHasExactNamedPath(root, testPath:_*)
    )
  }
}
