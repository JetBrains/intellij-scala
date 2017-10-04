package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.PropSpecGenerator

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait PropSpecSingleTestTest extends PropSpecGenerator {
  val propSpecTestPath = List("[root]", propSpecClassName, "Single tests should run")
  val propSpecTestTaggedPath = List("[root]", propSpecClassName, "tagged")

  def testPropSpec() {
    runTestByLocation(5, 5, propSpecFileName,
      checkConfigAndSettings(_, propSpecClassName, "Single tests should run"),
      root => checkResultTreeHasExactNamedPath(root, propSpecTestPath:_*) &&
          checkResultTreeDoesNotHaveNodes(root, "other tests should not run")
    )
  }

  def testTaggedPropSpec() {
    runTestByLocation(12, 5, propSpecFileName,
      checkConfigAndSettings(_, propSpecClassName, "tagged"),
      root => checkResultTreeHasExactNamedPath(root, propSpecTestTaggedPath:_*) &&
        checkResultTreeDoesNotHaveNodes(root, "other tests should not run")
    )
  }
}
