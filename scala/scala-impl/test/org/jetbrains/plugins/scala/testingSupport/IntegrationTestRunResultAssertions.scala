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

  def assertExitCode(expectedCode: Int, actualCode: Int): Unit =
    Assert.assertEquals("Test runner process terminated with unexpected error code $errorCode", expectedCode, actualCode)
}
