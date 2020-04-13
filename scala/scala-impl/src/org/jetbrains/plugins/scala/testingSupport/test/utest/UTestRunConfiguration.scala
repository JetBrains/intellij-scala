package org.jetbrains.plugins.scala
package testingSupport.test.utest

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testIntegration.TestFramework
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.testingSupport.test.sbt.{SbtCommandsBuilder, SbtCommandsBuilderBase, SbtTestRunningSupport, SbtTestRunningSupportBase}
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, SuiteValidityChecker, SuiteValidityCheckerBase, TestConfigurationUtil, TestFrameworkRunnerInfo}

import scala.annotation.tailrec

class UTestRunConfiguration(
  project: Project,
  configurationFactory: ConfigurationFactory,
  name: String
) extends AbstractTestRunConfiguration(
  project,
  configurationFactory,
  name
) {

  override val suitePaths: Seq[String] = UTestUtil.suitePaths

  override val testFramework: TestFramework = TestFramework.EXTENSION_NAME.findExtension(classOf[UTestTestFramework])

  override val configurationProducer: UTestConfigurationProducer = TestConfigurationUtil.uTestConfigurationProducer

  override protected val validityChecker: SuiteValidityChecker = new SuiteValidityCheckerBase {
    override protected def isValidClass(clazz: PsiClass): Boolean = clazz.isInstanceOf[ScObject]
    override protected def hasSuitableConstructor(clazz: PsiClass): Boolean = true
  }

  override protected val runnerInfo: TestFrameworkRunnerInfo = TestFrameworkRunnerInfo(
    classOf[org.jetbrains.plugins.scala.testingSupport.uTest.UTestRunner].getName
  )

  override protected val sbtSupport: SbtTestRunningSupport = new SbtTestRunningSupportBase {
    override def commandsBuilder: SbtCommandsBuilder = new SbtCommandsBuilderBase {
      override def classKey: Option[String] = Some("--")
      override def testNameKey: Option[String] = None
      override def escapeClassAndTest(input: String): String = quoteSpaces(input)
      override def escapeTestName(test: String): String = test.stripPrefix("tests").replace("\\", ".")
    }
  }
}
