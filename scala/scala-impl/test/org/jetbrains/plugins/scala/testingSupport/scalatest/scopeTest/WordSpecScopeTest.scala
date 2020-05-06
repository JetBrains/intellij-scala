package org.jetbrains.plugins.scala.testingSupport.scalatest.scopeTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.WordSpecGenerator

trait WordSpecScopeTest extends WordSpecGenerator {

  def testWordSpecEmptyScope(): Unit = {
    assertConfigAndSettings(createTestFromLocation(13, 10, wordSpecFileName), wordSpecClassName)
  }

  def testWordSpecScope(): Unit = {
    val testNames = Seq("WordSpecTest should Run single test", "WordSpecTest should ignore other tests")

    val path1 = List("[root]", wordSpecClassName, "WordSpecTest", "Run single test")
    val path2 = List("[root]", wordSpecClassName, "WordSpecTest", "ignore other tests")
    runTestByLocation2(3, 10, wordSpecFileName, assertConfigAndSettings(_, wordSpecClassName, testNames:_*),
      root => {
        assertResultTreeHasExactNamedPaths(root)(Seq(path1, path2))
        assertResultTreeDoesNotHaveNodes(root, "outer")
      })
  }
}
