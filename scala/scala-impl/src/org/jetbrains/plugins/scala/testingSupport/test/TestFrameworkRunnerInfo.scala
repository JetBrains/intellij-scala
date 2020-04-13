package org.jetbrains.plugins.scala.testingSupport.test

/**
 * see runners module for details
 * @param runnerClass fully qualified name of runner class
 * @param reporterClass fully qualified name of reporter class if some is intended to be parametrized in the runners
 */
case class TestFrameworkRunnerInfo(runnerClass: String, reporterClass: Option[String])

object TestFrameworkRunnerInfo {
  def apply(runnerClass: String, reporterClass: String): TestFrameworkRunnerInfo =
    new TestFrameworkRunnerInfo(runnerClass, Some(reporterClass))

  def apply(runnerClass: String): TestFrameworkRunnerInfo =
    new TestFrameworkRunnerInfo(runnerClass, None)
}
