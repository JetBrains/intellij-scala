package org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest.tagged

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.FlatSpecGenerator
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest.FlatSpecSingleTestTestBase

trait FlatSpecTaggedSingleTestTest extends FlatSpecSingleTestTestBase with FlatSpecGenerator {

  protected val flatSpecTaggedTestPath = TestNodePath("[root]", flatSpecClassName, "A FlatSpecTest", "should run tagged tests")

  def testTaggedFlatSpec(): Unit = {
    doTest(flatSpecClassName)(loc(flatSpecFileName, 18, 7))(
      "A FlatSpecTest should run tagged tests",
      flatSpecTaggedTestPath
    )
  }
}
