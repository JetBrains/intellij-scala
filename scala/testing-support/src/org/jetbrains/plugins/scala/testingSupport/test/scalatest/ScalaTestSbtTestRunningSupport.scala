package org.jetbrains.plugins.scala.testingSupport.test.scalatest

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.SettingMap
import org.jetbrains.plugins.scala.testingSupport.test.sbt.{SbtCommandsBuilder, SbtCommandsBuilderBase, SbtTestRunningSupportBase}
import org.jetbrains.sbt.shell.SbtShellCommunication

import scala.concurrent.Future

private class ScalaTestSbtTestRunningSupport extends SbtTestRunningSupportBase {

  override def allowsSbtUiRun: Boolean = true

  /**
   * @see [[https://www.scalatest.org/user_guide/using_scalatest_with_sbt]]
   */
  override def commandsBuilder: SbtCommandsBuilder = new SbtCommandsBuilderBase {
    override def testNameKey: Option[String] = Some("-- -t")
  }

  override def modifySbtSettingsForUi(module: Module, comm: SbtShellCommunication): Future[SettingMap] =
    for {
      x <- modifySbtSetting(comm, module, SettingMap(), "testOptions", "test", "Test", """Tests.Argument(TestFrameworks.ScalaTest, "-oDU")""", !_.contains("-oDU"))
      y <- modifySbtSetting(comm, module, x, "parallelExecution", "test", "Test", "false", !_.contains("false"), shouldSet = true)
    } yield y
}