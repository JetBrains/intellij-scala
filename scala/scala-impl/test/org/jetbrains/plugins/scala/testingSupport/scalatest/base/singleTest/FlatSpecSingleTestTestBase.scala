package org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest

import org.jetbrains.plugins.scala.configurations.TestLocation.CaretLocation
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait FlatSpecSingleTestTestBase extends ScalaTestTestCase {

  protected def doTest(testClassName: String)
                      (caretLocation: CaretLocation)
                      (expectedTestName: String, expectedTestPath: TestNodePath): Unit =
    runTestByLocation(
      caretLocation,
      configAndSettings => assertConfigAndSettings(configAndSettings, testClassName, expectedTestName),
      root => {
        assertResultTreeHasExactNamedPath(root, expectedTestPath)
        assertResultTreeDoesNotHaveNodes(root, "should not run other tests")
      }
    )
}
