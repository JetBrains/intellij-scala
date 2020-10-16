package org.jetbrains.plugins.scala.testingSupport

import org.junit.Assert

trait IntegrationTestRunResultAssertions {
  self: IntegrationTest =>

  trait ProcessOutputAssert extends Function1[ProcessOutput, Unit]

  trait TestRunResultAssert extends Function1[TestRunResult, Unit] {
    def and(g: TestRunResultAssert): TestRunResultAssert = x => {
      apply(x)
      g(x)
    }
  }

  protected def AssertResultTreePathsEqualsUnordered(
    expectedPaths: Iterable[TestNodePath]
  ): TestRunResultAssert = { root =>
    assertResultTreePathsEqualsUnordered(root.requireTestTreeRoot)(expectedPaths)
  }

  protected def AssertResultTreePathsEqualsUnordered2(
    expectedPaths: Iterable[TestNodePathWithStatus]
  ): TestRunResultAssert = { root =>
    assertResultTreePathsEqualsUnordered2(root.requireTestTreeRoot)(expectedPaths)
  }

  def assertExitCode(expectedCode: Int, res: TestRunResult): Unit =
    AssertExitCode(expectedCode)(res)

  def AssertExitCode(expectedCode: Int): TestRunResultAssert = res =>
    assertExitCode(expectedCode, res.processExitCode)

  def assertExitCode(expectedCode: Int, actualCode: Int): Unit = {
    // return code on Unix/Linux program is a single byte; it has a value between 0 and 255
    // -1 becomes 255, -2 becomes 254, etc...
    val expectedCodeFixed =
      if (com.intellij.openapi.util.SystemInfo.isUnix)
        expectedCode.toByte & 0xFF
      else
        expectedCode
    Assert.assertEquals(
      "Test runner process terminated with unexpected error code $errorCode",
      expectedCodeFixed,
      actualCode
    )
  }
}
