package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase
import org.junit.Assert._

trait FlatSpecSingleTestTestBase extends ScalaTestTestCase {

  protected def doTest(fileName: String, testClassName: String)
                      (lineNumber: Int, offset: Int)
                      (expectedTestName: String, expectedTestPath: Seq[String]): Unit = {
    runTestByLocation2(
      lineNumber, offset, fileName,
      configAndSettings => {
        assertConfigAndSettings(configAndSettings, testClassName, expectedTestName)
        true
      },
      root => {
        val expectedTestPathFinal = preprocessSingleFlatSpecExpectedPath(expectedTestPath)
        assertTrue(
          s"result tree doesn't contain test name with path: $expectedTestPathFinal",
          checkResultTreeHasExactNamedPath(root, expectedTestPathFinal: _*)
        )
        val unexpectedTestName = "should not run other tests"
        assertTrue(
          s"result tree contained unexpected test name: `$unexpectedTestName`",
          checkResultTreeDoesNotHaveNodes(root, unexpectedTestName)
        )
        true
      }
    )
  }

  protected def preprocessSingleFlatSpecExpectedPath(path: Seq[String]): Seq[String] = path
}
