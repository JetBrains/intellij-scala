package org.jetbrains.plugins.scala.testingSupport.test.sbt

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.{SettingEntry, SettingMap}
import org.jetbrains.sbt.shell.{SbtShellCommunication, SettingQueryHandler}
import org.jetbrains.sbt.{SbtUtil, SbtVersionCapabilities}

import scala.concurrent.{ExecutionContextExecutor, Future}

abstract class SbtTestRunningSupportBase extends SbtTestRunningSupport {

  override implicit def executionContext: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  override def allowsSbtUiRun: Boolean = false

  override def initialize(comm: SbtShellCommunication): Future[String] =
    comm.command("initialize")

  override def modifySbtSettingsForUi(module: Module, comm: SbtShellCommunication): Future[SettingMap] =
    Future.successful(Map.empty)

   private def createSettingQueryHandler(settingsEntry: SettingEntry, comm: SbtShellCommunication): SettingQueryHandler =
    new SettingQueryHandler(
      sbtProjectUriAndId = settingsEntry.sbtProjectUriAndId,
      settingName = settingsEntry.settingName,
      comm = comm
    )

  override def resetSbtSettingsForUi(
    comm: SbtShellCommunication,
    oldSettings: SettingMap
  ): Future[Boolean] = {
    val futures = for ((settingEntry, value) <- oldSettings)
      yield createSettingQueryHandler(settingEntry, comm).setSettingValue(value)
    Future.sequence(futures).map(_.forall(identity))
  }


  override def modifySbtSetting(
    comm: SbtShellCommunication,
    module: Module,
    settings: SettingMap,
    setting: String,
    value: String,
    modificationCondition: String => Boolean,
    shouldSet: Boolean,
    shouldRevert: Boolean
  ): Future[SettingMap] = {
    val sbtProjectUriAndId = SbtUtil.getSbtProjectUriAndId(module)

    val showHandler = new SettingQueryHandler(sbtProjectUriAndId, setting, comm)
    val setHandler = new SettingQueryHandler(sbtProjectUriAndId, setting, comm)

    for {
      oldSettings <- showHandler.getSettingValue
      success <- {
        if (modificationCondition(oldSettings))
          if (shouldSet) setHandler.setSettingValue(value)
          else setHandler.addToSettingValue(value)
        else
          Future.successful(true)
      }
      //TODO: meaningful report if settings were not set correctly
      settings <- {
        //TODO check 'opts.nonEmpty()' is required so that we don't try to mess up settings if nothing was read
        if (success)
          if (shouldRevert && oldSettings.nonEmpty && modificationCondition(oldSettings)) {
            val settingEntry = SettingEntry(setting, sbtProjectUriAndId)
            Future.successful(settings + (settingEntry -> oldSettings))
          }
          else
            Future.successful(settings)
        else
          Future.failed[SettingMap](new RuntimeException("Failed to modify sbt project settings"))
      }
    } yield settings
  }
}
