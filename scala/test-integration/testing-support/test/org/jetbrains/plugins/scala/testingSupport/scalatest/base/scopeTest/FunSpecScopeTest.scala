package org.jetbrains.plugins.scala.testingSupport.scalatest.base.scopeTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.FunSpecGenerator

trait FunSpecScopeTest extends FunSpecGenerator {

  def testFunSpecEmptyScope(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(17, 15, funSpecFileName), funSpecClassName)
  }

  def testFunSpecScope(): Unit = {
    val testNames = Seq("FunSpecTest should launch single test", "FunSpecTest should not launch other tests")

    val path1 = TestNodePath("[root]", funSpecClassName, "FunSpecTest", "should launch single test")
    val path2 = TestNodePath("[root]", funSpecClassName, "FunSpecTest", "should not launch other tests")
    runTestByLocation(loc(funSpecFileName, 3, 15), assertConfigAndSettings(_, funSpecClassName, testNames:_*),
      root => {
        assertResultTreeHasExactNamedPaths(root)(Seq(path1, path2))
        assertResultTreeDoesNotHaveNodes(root, "OtherScope")
      })
  }
}
