package org.jetbrains.plugins.scala.testingSupport.scalatest.base.scopeTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.WordSpecGenerator

trait WordSpecScopeTest extends WordSpecGenerator {

  def testWordSpecEmptyScope(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(13, 10, wordSpecFileName), wordSpecClassName)
  }

  def testWordSpecScope(): Unit = {
    val testNames = Seq("WordSpecTest should Run single test", "WordSpecTest should ignore other tests")

    val path1 = TestNodePath("[root]", wordSpecClassName, "WordSpecTest", "Run single test")
    val path2 = TestNodePath("[root]", wordSpecClassName, "WordSpecTest", "ignore other tests")
    runTestByLocation(loc(wordSpecFileName, 3, 10), assertConfigAndSettings(_, wordSpecClassName, testNames:_*),
      root => {
        assertResultTreeHasExactNamedPaths(root)(Seq(path1, path2))
        assertResultTreeDoesNotHaveNodes(root, "outer")
      })
  }
}
