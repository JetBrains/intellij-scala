package org.jetbrains.plugins.scala.testingSupport.scalatest

trait NestedSameNamesTest extends ScalaTestTestCase {

  val testPath = TestNodePath("[root]", "FunSpecTest", "FunSpecTest", "FunSpecTest", "FunSpecTest")

  addSourceFile("FunSpecTest.scala",
    """
      |import org.scalatest._
      |
      |class FunSpecTest extends FunSpec {
      |  describe("FunSpecTest") {
      |    describe("FunSpecTest") {
      |      it ("FunSpecTest") {
      |        print("$TestOutputPrefix OK $TestOutputSuffix")
      |      }
      |    }
      |  }
      |}
    """.stripMargin
  )
  def testNestedSameNames(): Unit = {
    runTestByLocation(loc("FunSpecTest.scala", 6, 12),
      assertConfigAndSettings(_, "FunSpecTest", "FunSpecTest FunSpecTest FunSpecTest"),
      root => assertResultTreeHasExactNamedPath(root, testPath)
    )
  }
}
