package org.jetbrains.plugins.scala.testingSupport.scalatest.scopeTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FreeSpecGenerator

/**
 * @author Roman.Shein
 * @since 05.05.2015.
 */
trait FreeSpecScopeTest extends FreeSpecGenerator {
  def testFreeSpecEmptyScope() {
    assert(checkConfigAndSettings(createTestFromLocation(31, 7, complexFreeSpecFileName), complexFreeSpecClassName))
  }

  def testFreeSpecScope() {
    val testNames = Seq("A ComplexFreeSpec Outer scope 2 Inner scope 2 Another innermost scope",
        "A ComplexFreeSpec Outer scope 2 Inner test")
    val path1 = List("[root]", complexFreeSpecClassName, "A ComplexFreeSpec", "Outer scope 2", "Inner scope 2",
      "Another innermost scope")
    val path2 = List("[root]", complexFreeSpecClassName, "A ComplexFreeSpec", "Outer scope 2", "Inner test")
    runTestByLocation(10, 10, complexFreeSpecFileName, checkConfigAndSettings(_, complexFreeSpecClassName,
      testNames:_*), root => checkResultTreeHasExactNamedPath(root, path1:_*) &&
      checkResultTreeHasExactNamedPath(root, path2:_*) &&
      checkResultTreeDoesNotHaveNodes(root, "Innermost scope", "Outer scope 3"))
  }
}
