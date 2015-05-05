package org.jetbrains.plugins.scala.testingSupport.scalatest.scopeTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FreeSpecGenerator

/**
 * @author Roman.Shein
 * @since 05.05.2015.
 */
trait FreeSpecScopeTest extends FreeSpecGenerator {
  def testFreeSpecEmptyScope() {
    addComplexFreeSpec()

    assert(checkConfigAndSettings(createTestFromLocation(31, 7, "ComplexFreeSpec.scala"), "ComplexFreeSpec"))
  }

  def testFreeSpecScope() {
    addComplexFreeSpec()

    val testName = "A ComplexFreeSpec Outer scope 2 Inner scope 2 Another innermost scope\n"+
        "A ComplexFreeSpec Outer scope 2 Inner test"
    val path1 = List("[root]", "ComplexFreeSpec", "A ComplexFreeSpec", "Outer scope 2", "Inner scope 2",
      "Another innermost scope")
    val path2 = List("[root]", "ComplexFreeSpec", "A ComplexFreeSpec", "Outer scope 2", "Inner test")
    runTestByLocation(10, 10, "ComplexFreeSpec.scala", checkConfigAndSettings(_, "ComplexFreeSpec", testName),
      root => checkResultTreeHasExactNamedPath(root, path1:_*) && checkResultTreeHasExactNamedPath(root, path2:_*) &&
      checkResultTreeDoesNotHaveNodes(root, "Innermost scope", "Outer scope 3"))
  }
}
