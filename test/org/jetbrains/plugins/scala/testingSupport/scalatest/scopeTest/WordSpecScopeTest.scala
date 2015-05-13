package org.jetbrains.plugins.scala.testingSupport.scalatest.scopeTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.WordSpecGenerator

/**
 * @author Roman.Shein
 * @since 05.05.2015.
 */
trait WordSpecScopeTest extends WordSpecGenerator {
  def testWordSpecEmptyScope() = {
    addWordSpec()

    assert(checkConfigAndSettings(createTestFromLocation(13, 10, wordSpecFileName), wordSpecClassName))
  }

  def testWordSpecScope() {
    addWordSpec()

    val testName = "WordSpecTest should Run single test\nWordSpecTest should ignore other tests"

    val path1 = List("[root]", "WordSpecTest", "WordSpecTest", "Run single test")
    val path2 = List("[root]", "WordSpecTest", "WordSpecTest", "ignore other tests")
    runTestByLocation(3, 10, wordSpecFileName, checkConfigAndSettings(_, wordSpecClassName, testName),
      root => checkResultTreeHasExactNamedPath(root, path1:_*) && checkResultTreeHasExactNamedPath(root, path2:_*) &&
          checkResultTreeDoesNotHaveNodes(root, "outer"))
  }
}
