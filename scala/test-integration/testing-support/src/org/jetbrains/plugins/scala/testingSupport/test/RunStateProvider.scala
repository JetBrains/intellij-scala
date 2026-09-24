package org.jetbrains.plugins.scala.testingSupport.test

import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import org.jetbrains.plugins.scala.testingSupport.test.CustomTestRunnerBasedStateProvider.TestFrameworkRunnerInfo
import org.jetbrains.plugins.scala.testingSupport.test.sbt.SbtTestRunningSupport

trait RunStateProvider {

  def commandLineState(
    env: ExecutionEnvironment,
    failedTests: Option[Seq[(String, String)]]
  ): RunProfileState
}

final class CustomTestRunnerOrSbtShellStateProvider(
  configuration: AbstractTestRunConfiguration,
  runnerInfo: TestFrameworkRunnerInfo,
  val sbtSupport: SbtTestRunningSupport
) extends RunStateProvider {

  override def commandLineState(
    env: ExecutionEnvironment,
    failedTests: Option[Seq[(String, String)]]
  ): RunProfileState = {
    val provider = if (configuration.testConfigurationData.useSbt)
      new SbtShellBasedStateProvider(configuration, sbtSupport)
    else
      new CustomTestRunnerBasedStateProvider(configuration, runnerInfo)
    provider.commandLineState(env, failedTests)
  }
}

final class CustomTestRunnerBasedStateProvider(
  configuration: AbstractTestRunConfiguration,
  runnerInfo: TestFrameworkRunnerInfo,
) extends RunStateProvider {

  override def commandLineState(
    env: ExecutionEnvironment,
    failedTests: Option[Seq[(String, String)]]
  ): RunProfileState =
    new ScalaTestFrameworkCommandLineState(configuration, env, failedTests, runnerInfo)
}

object CustomTestRunnerBasedStateProvider {
  /**
   * See runners module for details
   * @param runnerClass fully qualified name of runner class
   */
  case class TestFrameworkRunnerInfo(runnerClass: String)

  object TestFrameworkRunnerInfo {
    def apply(runnerClass: Class[?]): TestFrameworkRunnerInfo =
      TestFrameworkRunnerInfo(runnerClass.getName)
  }
}

final class SbtShellBasedStateProvider(
  configuration: AbstractTestRunConfiguration,
  val sbtSupport: SbtTestRunningSupport,
) extends RunStateProvider {

  override def commandLineState(
    env: ExecutionEnvironment,
    failedTests: Option[Seq[(String, String)]]
  ): RunProfileState =
    new ScalaTestFrameworkCommandLineSbtState(configuration, env, failedTests, sbtSupport)
}
