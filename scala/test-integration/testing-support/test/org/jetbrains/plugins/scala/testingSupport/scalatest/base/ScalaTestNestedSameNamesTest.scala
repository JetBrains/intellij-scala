package org.jetbrains.plugins.scala.testingSupport.scalatest.base


trait ScalaTestNestedSameNamesTest extends ScalaTestTestCase {

  private val testPath = TestNodePath("[root]", "FunSpecTest", "FunSpecTest", "FunSpecTest", "FunSpecTest")

  addSourceFile("FunSpecTest.scala",
    s"""$ImportsForFunSpec
       |
       |class FunSpecTest extends $FunSpecBase {
       |  describe("FunSpecTest") {
       |    describe("FunSpecTest") {
       |      it ("FunSpecTest") {
       |        print("$TestOutputPrefix OK $TestOutputSuffix")
       |      }
       |    }
       |  }
       |}
       |""".stripMargin
  )
  def testNestedSameNames(): Unit = {
    runTestByLocation(loc("FunSpecTest.scala", 6, 12),
      assertConfigAndSettings(_, "FunSpecTest", "FunSpecTest FunSpecTest FunSpecTest"),
      root => assertResultTreeHasExactNamedPath(root, testPath)
    )
  }
}
