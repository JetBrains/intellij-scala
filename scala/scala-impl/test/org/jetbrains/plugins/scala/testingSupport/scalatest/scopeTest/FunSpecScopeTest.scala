package org.jetbrains.plugins.scala.testingSupport.scalatest.scopeTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FunSpecGenerator

trait FunSpecScopeTest extends FunSpecGenerator {

  def testFunSpecEmptyScope(): Unit = {
    assertConfigAndSettings(createTestFromLocation(17, 15, funSpecFileName), funSpecClassName)
  }

  def testFunSpecScope(): Unit = {
    val testNames = Seq("FunSpecTest should launch single test", "FunSpecTest should not launch other tests")

    val path1 = List("[root]", funSpecClassName, "FunSpecTest", "should launch single test")
    val path2 = List("[root]", funSpecClassName, "FunSpecTest", "should not launch other tests")
    runTestByLocation(3, 15, funSpecFileName, checkConfigAndSettings(_, funSpecClassName, testNames:_*),
      root => checkResultTreeHasExactNamedPath(root, path1:_*) &&
        checkResultTreeHasExactNamedPath(root, path2:_*) &&
        checkResultTreeDoesNotHaveNodes(root, "OtherScope"))
  }
}
