package org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.PropSpecGenerator

trait PropSpecSingleTestTest extends PropSpecGenerator {

  protected val propSpecTestPath = TestNodePath("[root]", propSpecClassName, "Single tests should run")
  protected val propSpecTestTaggedPath = TestNodePath("[root]", propSpecClassName, "tagged")

  def testPropSpec(): Unit = {
    runTestByLocation(loc(propSpecFileName, 5, 5),
      assertConfigAndSettings(_, propSpecClassName, "Single tests should run"),
      root => {
        assertResultTreeHasExactNamedPath(root, propSpecTestPath)
        assertResultTreeDoesNotHaveNodes(root, "other tests should not run")
      }
    )
  }

  def testTaggedPropSpec(): Unit = {
    runTestByLocation(loc(propSpecFileName, 12, 5),
      assertConfigAndSettings(_, propSpecClassName, "tagged"),
      root => {
        assertResultTreeHasExactNamedPath(root, propSpecTestTaggedPath)
        assertResultTreeDoesNotHaveNodes(root, "other tests should not run")
      }
    )
  }
}
