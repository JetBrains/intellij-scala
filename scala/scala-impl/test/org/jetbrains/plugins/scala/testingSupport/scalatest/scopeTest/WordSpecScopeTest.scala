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
    runTestByLocation(3, 10, wordSpecFileName, checkConfigAndSettings(_, wordSpecClassName, testNames:_*),
      root => checkResultTreeHasExactNamedPath(root, path1:_*) &&
        checkResultTreeHasExactNamedPath(root, path2:_*) &&
        checkResultTreeDoesNotHaveNodes(root, "outer"))
  }
}
