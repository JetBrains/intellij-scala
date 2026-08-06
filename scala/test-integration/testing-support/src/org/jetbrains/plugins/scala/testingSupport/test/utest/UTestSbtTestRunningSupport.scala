package org.jetbrains.plugins.scala.testingSupport.test.utest

import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.testingSupport.test.sbt.{SbtCommandsBuilder, SbtTestRunningSupportBase}
import org.jetbrains.plugins.scala.testingSupport.test.utest.UTestSbtTestRunningSupport.UTestSbtCommandsBuilder
import org.jetbrains.plugins.scala.testingSupport.test.utils.StringOps

private class UTestSbtTestRunningSupport extends SbtTestRunningSupportBase {
  override def commandsBuilder: SbtCommandsBuilder = new UTestSbtCommandsBuilder
}

object UTestSbtTestRunningSupport {

  /**
   * uTest removes the distinction between "test suite" and "test case".
   *
   * It uses single argument for test selection, examples: {{{
   *   testOnly -- org.mypackage
   *   testOnly -- org.mypackage.ExampleTestsSuite
   *   testOnly -- org.mypackage.ExampleTestsSuite.tests
   *   testOnly -- org.mypackage.ExampleTestsSuite.tests.testCaseNumberOne
   * }}}
   *
   * @see [[https://www.lihaoyi.com/post/uTesttheEssentialTestFrameworkforScala.html#test-running]]
   */
  @TestOnly
  final class UTestSbtCommandsBuilder extends SbtCommandsBuilder {

    override def buildTestOnly(classToTests: Map[String, Set[String]]): Seq[String] = {
      val testLocations = classToTests.flatMap((toUTestTestLocations _).tupled).toSeq
      val testLocationsEscaped = testLocations.map(_.withQuotedSpaces)
      testLocationsEscaped.map("-- " + _)
    }

    private def toUTestTestLocations(clazz: String, tests: Set[String]): Iterable[String] = {
      val classClean = clazz.withoutBackticks
      if (tests.isEmpty)
        Seq(classClean)
      else
        tests.map(classClean + escapeTestName(_).trim).toSeq
    }

    private def escapeTestName(test: String): String =
      test.stripPrefix("tests").replace("\\", ".")
  }
}