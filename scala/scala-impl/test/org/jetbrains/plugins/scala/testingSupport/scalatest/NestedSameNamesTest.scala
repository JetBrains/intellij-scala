package org.jetbrains.plugins.scala.testingSupport.scalatest

trait NestedSameNamesTest extends ScalaTestTestCase {

  val testPath = List("[root]", "FunSpecTest", "FunSpecTest", "FunSpecTest", "FunSpecTest")

  addSourceFile("FunSpecTest.scala",
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
  def testNestedSameNames(): Unit = {
    runTestByLocation(6, 12, "FunSpecTest.scala",
      checkConfigAndSettings(_, "FunSpecTest", "FunSpecTest FunSpecTest FunSpecTest"),
      root => checkResultTreeHasExactNamedPath(root, testPath: _*)
    )
  }
}
