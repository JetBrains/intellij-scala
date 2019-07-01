package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest.tagged

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FlatSpecGenerator
import org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest.FlatSpecSingleTestTestBase

/**
 * @author Roman.Shein
 *         Date: 09.11.2016
 */
trait FlatSpecTaggedSingleTestTest extends FlatSpecSingleTestTestBase with FlatSpecGenerator {
  val flatSpecTaggedTestPath = List("[root]", flatSpecClassName, "A FlatSpecTest", "should run tagged tests")

  def testTaggedFlatSpec() {
    doTest(flatSpecFileName, flatSpecClassName)(18, 7)(
      "A FlatSpecTest should run tagged tests",
      flatSpecTaggedTestPath
    )
  }
}
