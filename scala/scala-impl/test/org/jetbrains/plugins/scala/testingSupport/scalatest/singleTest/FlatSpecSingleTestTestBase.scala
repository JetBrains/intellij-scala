package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase
import org.junit.Assert._

trait FlatSpecSingleTestTestBase extends ScalaTestTestCase {

  protected def doTest(fileName: String, testClassName: String)
                      (lineNumber: Int, offset: Int)
                      (expectedTestName: String, expectedTestPath: Seq[String]): Unit =
    runTestByLocation2(
      lineNumber, offset, fileName,
      configAndSettings => assertConfigAndSettings(configAndSettings, testClassName, expectedTestName),
      root => {
        val expectedTestPathFinal = preprocessSingleFlatSpecExpectedPath(expectedTestPath)
        assertResultTreeHasExactNamedPath(root, expectedTestPathFinal)
        assertResultTreeDoesNotHaveNodes(root, "should not run other tests")
      }
    )

  protected def preprocessSingleFlatSpecExpectedPath(path: Seq[String]): Seq[String] = path
}
