package org.jetbrains.plugins.scala.testingSupport.scalatest.base

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude


trait ScalaTestNestedSameNamesTest extends ScalaTestTestCase {

  private val testPath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "FunSpecTest", "FunSpecTest", "FunSpecTest", "FunSpecTest")

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
      root => assertResultTreeHasSinglePath(root, testPath)
    )
  }
}
