package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FunSpecGenerator

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait FunSpecSingleTestTest extends FunSpecGenerator {
  val funSpecTestPath = List("[root]", funSpecClassName, "FunSpecTest", "should launch single test")
  val funSpecTaggedTestPath = List("[root]", funSpecClassName, "taggedScope", "is tagged")

  def testFunSpec() {
    runTestByLocation(5, 9, funSpecFileName,
      checkConfigAndSettings(_, funSpecClassName, "FunSpecTest should launch single test"),
      root => checkResultTreeHasExactNamedPath(root, funSpecTestPath:_*) &&
          checkResultTreeDoesNotHaveNodes(root, "should not launch other tests")
    )
  }

  def testTaggedFunSpec() {
    runTestByLocation(20, 6, funSpecFileName,
      checkConfigAndSettings(_, funSpecClassName, "taggedScope is tagged"),
      root => checkResultTreeHasExactNamedPath(root, funSpecTaggedTestPath:_*) &&
        checkResultTreeDoesNotHaveNodes(root, "should not launch other tests")
    )
  }
}
