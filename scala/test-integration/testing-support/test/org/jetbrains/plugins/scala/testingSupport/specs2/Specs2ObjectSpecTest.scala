package org.jetbrains.plugins.scala.testingSupport.specs2

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude

abstract class Specs2ObjectSpecTest extends Specs2TestCase {

  addSourceFile("SpecObject.scala",
    s"""
       |import org.specs2.mutable.Specification
       |
       |object SpecObject extends Specification {
       |  "single test in SpecObject" should {
       |    "run alone" in {
       |      print("$TestOutputPrefix OK $TestOutputSuffix")
       |      true must_== true
       |    }
       |
       |    "ignore other test" in {
       |      print("$TestOutputPrefix FAILED $TestOutputSuffix")
       |      true must_== true
       |    }
       |  }
       |}
    """.stripMargin)
  def testSpecObject(): Unit = {
    runTestByLocation(loc("SpecObject.scala", 5, 8),
      assertConfigAndSettings(_, "SpecObject", "run alone"),
      root => {
        assertResultTreeHasSinglePath(root, TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "SpecObject", "single test in SpecObject should", "run alone"))
      }
    )

  }
}
