package org.jetbrains.plugins.scala
package testingSupport
package specs2

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude

abstract class SCL7228Test extends Specs2TestCase {

  addSourceFile("SCL7228Test.scala",
    """
      |import org.specs2.mutable.Specification
      |
      |class SCL7228Test extends Specification {
      |  override def is = "foo (bar)" ! (true == true)
      |}
    """.stripMargin
  )

  def testScl7228(): Unit =
    runTestByLocation(loc("SCL7228Test.scala", 3, 1),
      assertConfigAndSettings(_, "SCL7228Test"),
      assertResultTreeHasSinglePath(_, TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "SCL7228Test", "foo (bar)"))
    )
}
