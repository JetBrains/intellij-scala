package org.jetbrains.plugins.scala.testingSupport.test.sbt

import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.SettingMap
import org.jetbrains.sbt.shell.SbtShellCommunication

import scala.concurrent.{ExecutionContext, Future}

@ApiStatus.Internal
trait SbtTestRunningSupport {

  implicit def executionContext: ExecutionContext

  def allowsSbtUiRun: Boolean

  def initialize(comm: SbtShellCommunication): Future[String]

  def commandsBuilder: SbtCommandsBuilder

  /** @return old sbt settings */
  def modifySbtSettingsForUi(module: Module, comm: SbtShellCommunication): Future[SettingMap]

  /** @return old sbt settings */
  protected def modifySbtSetting(
    comm: SbtShellCommunication,
    module: Module,
    settings: SettingMap,
    setting: String,
    showTaskName: String,
    setTaskName: String,
    value: String,
    modificationCondition: String => Boolean,
    shouldSet: Boolean = false,
    shouldRevert: Boolean = true
  ): Future[SettingMap]

  def resetSbtSettingsForUi(
    comm: SbtShellCommunication,
    oldSettings: SettingMap
  ): Future[Boolean]
}