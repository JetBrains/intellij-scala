package org.jetbrains.plugins.scala.testingSupport.test.munit

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.testframework.sm.runner.{SMRunnerConsolePropertiesProvider, SMTRunnerConsoleProperties}
import com.intellij.execution.{CommonJavaRunConfigurationParameters, Executor}
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.testingSupport.test._
import org.jetbrains.plugins.scala.testingSupport.test.testdata.TestConfigurationData

/**
 * We need to implement [[CommonJavaRunConfigurationParameters]] to automatically enable "Run with Profiler" action. See:
 *  - [[com.intellij.profiler.ultimate.DefaultJavaProfilerStarterExtension.canRun]]
 *  - [[com.intellij.profiler.ultimate.jfr.JFRConfigurationExtension.isApplicableFor]]
 *  - SCL-21891
 * Also [[com.intellij.execution.JavaTestFrameworkRunnableState]] requires configuration to implement [[CommonJavaRunConfigurationParameters]]
 */
final class MUnitConfiguration(
  project: Project,
  configurationFactory: ConfigurationFactory,
  name: String
) extends AbstractTestRunConfiguration(
  project,
  configurationFactory,
  name
) with CommonJavaRunConfigurationParameters
  with DelegateCommonJavaRunConfigurationParameters
  with SMRunnerConsolePropertiesProvider {

  override protected def delegateToTestData: TestConfigurationData = testConfigurationData

  override val testFramework: MUnitTestFramework = MUnitTestFramework()

  override val configurationProducer: AbstractTestConfigurationProducer[_] = MUnitConfigurationProducer()

  override protected def validityChecker: SuiteValidityChecker = new SuiteValidityCheckerBase

  @NotNull
  override def createTestConsoleProperties(executor: Executor): SMTRunnerConsoleProperties = {
    // NOTE: we currently do not support sbt for MUnit, so always returning non-null value
    // see SCL-18257 and CWM-1987
    new MUnitConsoleProperties(this, executor)
  }

  override def runStateProvider: RunStateProvider =
    (env, failedTests) => {
      new MUnitCommandLineState(this, env, failedTests)
    }
}

