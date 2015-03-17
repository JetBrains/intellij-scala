package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.WordSpecGenerator

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait WordSpecSingleTestTest extends WordSpecGenerator {
  val wordSpecTestPath = List("[root]", "WordSpecTest", "WordSpecTest", "Run single test")

  def testWordSpec() {
    addWordSpec()

    runTestByLocation(5, 10, "WordSpecTest.scala",
      checkConfigAndSettings(_, "WordSpecTest", "WordSpecTest should Run single test"),
      root => checkResultTreeHasExactNamedPath(root, wordSpecTestPath:_*) &&
          checkResultTreeDoesNotHaveNodes(root, "ignore other tests"),
      debug = true
    )
  }
}
