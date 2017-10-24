package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.WordSpecGenerator

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait WordSpecSingleTestTest extends WordSpecGenerator {
  val wordSpecTestPath = List("[root]", wordSpecClassName, "WordSpecTest", "Run single test")
  val wordSpecTestTaggedPath = List("[root]", wordSpecClassName, "tagged", "be tagged")

  def testWordSpec() {
    runTestByLocation(5, 10, wordSpecFileName,
      checkConfigAndSettings(_, wordSpecClassName, "WordSpecTest should Run single test"),
      root => checkResultTreeHasExactNamedPath(root, wordSpecTestPath:_*) &&
          checkResultTreeDoesNotHaveNodes(root, "ignore other tests")
    )
  }

  def testTaggedWordSpec() {
    runTestByLocation(20, 6, wordSpecFileName,
      checkConfigAndSettings(_, wordSpecClassName, "tagged should be tagged"),
      root => checkResultTreeHasExactNamedPath(root, wordSpecTestTaggedPath:_*) &&
        checkResultTreeDoesNotHaveNodes(root, "ignore other tests")
    )
  }
}
