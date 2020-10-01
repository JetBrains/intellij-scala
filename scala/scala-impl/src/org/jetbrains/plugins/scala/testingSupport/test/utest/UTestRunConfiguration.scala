package org.jetbrains.plugins.scala
package testingSupport.test.utest

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.TestFrameworkRunnerInfo
import org.jetbrains.plugins.scala.testingSupport.test.sbt.{SbtCommandsBuilder, SbtTestRunningSupport, SbtTestRunningSupportBase}
import org.jetbrains.plugins.scala.testingSupport.test.utest.UTestRunConfiguration.UTestSbtCommandsBuilder
import org.jetbrains.plugins.scala.testingSupport.test.utils.StringOps
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, SuiteValidityChecker, SuiteValidityCheckerBase}

class UTestRunConfiguration(
  project: Project,
  configurationFactory: ConfigurationFactory,
  name: String
) extends AbstractTestRunConfiguration(
  project,
  configurationFactory,
  name
) {

  override val testFramework: UTestTestFramework = UTestTestFramework()

  override val configurationProducer: UTestConfigurationProducer = UTestConfigurationProducer()

  override protected val validityChecker: SuiteValidityChecker = new SuiteValidityCheckerBase {
    override protected def isValidClass(clazz: PsiClass): Boolean = clazz.isInstanceOf[ScObject]
    override protected def hasSuitableConstructor(clazz: PsiClass): Boolean = true
  }

  override protected val runnerInfo: TestFrameworkRunnerInfo = TestFrameworkRunnerInfo(
    classOf[org.jetbrains.plugins.scala.testingSupport.uTest.UTestRunner].getName
  )

  override val sbtSupport: SbtTestRunningSupport = new SbtTestRunningSupportBase {
    override def commandsBuilder: SbtCommandsBuilder = new UTestSbtCommandsBuilder
  }
}

object UTestRunConfiguration {

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
