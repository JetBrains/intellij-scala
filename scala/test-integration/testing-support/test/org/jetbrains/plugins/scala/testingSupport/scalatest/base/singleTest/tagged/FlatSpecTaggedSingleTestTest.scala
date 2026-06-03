package org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest.tagged

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.FlatSpecGenerator
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest.FlatSpecSingleTestTestBase

trait FlatSpecTaggedSingleTestTest extends FlatSpecSingleTestTestBase with FlatSpecGenerator {

  protected val flatSpecTaggedTestPath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", flatSpecClassName, "A FlatSpecTest", "should run tagged tests")

  def testTaggedFlatSpec(): Unit = {
    doTest(flatSpecClassName)(loc(flatSpecFileName, 18, 7))(
      "A FlatSpecTest should run tagged tests",
      flatSpecTaggedTestPath
    )
  }
}
