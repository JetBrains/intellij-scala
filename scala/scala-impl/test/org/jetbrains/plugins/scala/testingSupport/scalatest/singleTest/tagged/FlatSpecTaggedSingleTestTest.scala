package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest.tagged

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FlatSpecGenerator

/**
  * @author Roman.Shein
  *         Date: 09.11.2016
  */
trait FlatSpecTaggedSingleTestTest extends FlatSpecGenerator {
  val flatSpecTaggedTestPath = List("[root]", flatSpecClassName, "A FlatSpecTest", "should run tagged tests")

  def testTaggedFlatSpec() {
    runTestByLocation(18, 7, flatSpecFileName,
      checkConfigAndSettings(_, flatSpecClassName, "A FlatSpecTest should run tagged tests"),
      root => checkResultTreeHasExactNamedPath(root, flatSpecTaggedTestPath:_*) &&
        checkResultTreeDoesNotHaveNodes(root, "should not run other tests")
    )
  }
}
