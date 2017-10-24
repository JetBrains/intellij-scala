package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest.tagged

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FreeSpecGenerator

/**
  * @author Roman.Shein
  *         Date: 09.11.2016
  */
trait FreeSpecTaggedSingleTestTest extends FreeSpecGenerator {
  val freeSpecTaggedTestPath = List("[root]", freeSpecClassName, "A FreeSpecTest", "can be tagged")

  def testTaggedFreeSpec(): Unit = {
    runTestByLocation(12, 7, freeSpecFileName,
      checkConfigAndSettings(_, freeSpecClassName, "A FreeSpecTest can be tagged"),
      root => checkResultTreeHasExactNamedPath(root, freeSpecTaggedTestPath:_*) &&
        checkResultTreeDoesNotHaveNodes(root, "should not run tests that are not selected")
    )
  }
}
