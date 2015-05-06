package org.jetbrains.plugins.scala.testingSupport.scalatest.scopeTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FunSpecGenerator

/**
 * @author Roman.Shein
 * @since 05.05.2015.
 */
trait FunSpecScopeTest extends FunSpecGenerator {
  def testFunSpecEmptyScope() {
    addFunSpec()

    assert(checkConfigAndSettings(createTestFromLocation(17, 15, funSpecFileName), funSpecClassName))
  }

  def testFunSpecScope() {
    addFunSpec()

    val testName = "FunSpecTest should launch single test\nFunSpecTest should not launch other tests"

    val path1 = List("[root]", "FunSpecTest", "FunSpecTest", "should launch single test")
    val path2 = List("[root]", "FunSpecTest", "FunSpecTest", "should not launch other tests")
    runTestByLocation(3, 15, funSpecFileName, checkConfigAndSettings(_, funSpecClassName, testName),
      root => checkResultTreeHasExactNamedPath(root, path1:_*) && checkResultTreeHasExactNamedPath(root, path2:_*) &&
      checkResultTreeDoesNotHaveNodes(root, "OtherScope"))
  }

}
