package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.WordSpecGenerator

trait WordSpecSingleTestTest extends WordSpecGenerator {

  val wordSpecTestPath = TestNodePath("[root]", wordSpecClassName, "WordSpecTest", "Run single test")
  val wordSpecTestTaggedPath = TestNodePath("[root]", wordSpecClassName, "tagged", "be tagged")

  def testWordSpec(): Unit =
    runTestByLocation(loc(wordSpecFileName, 5, 10),
      assertConfigAndSettings(_, wordSpecClassName, "WordSpecTest should Run single test"),
      root => {
        assertResultTreeHasExactNamedPath(root, wordSpecTestPath)
        assertResultTreeDoesNotHaveNodes(root, "ignore other tests")
      }
    )

  def testTaggedWordSpec(): Unit =
    runTestByLocation(loc(wordSpecFileName, 20, 6),
      assertConfigAndSettings(_, wordSpecClassName, "tagged should be tagged"),
      root => {
        assertResultTreeHasExactNamedPath(root, wordSpecTestTaggedPath)
        assertResultTreeDoesNotHaveNodes(root, "ignore other tests")
      }
    )
}
