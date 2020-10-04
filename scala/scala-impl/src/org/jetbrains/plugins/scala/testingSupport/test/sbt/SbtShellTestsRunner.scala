package org.jetbrains.plugins.scala.testingSupport.test.sbt

import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.SettingMap
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.shell.{SbtShellCommunication, SettingQueryHandler}

import scala.concurrent.{ExecutionContext, Future}

@ApiStatus.Internal
object SbtShellTestsRunner {

  def runTestsInSbtShell(
    sbtSupport: SbtTestRunningSupport,
    module: Module,
    suitesToTestsMap: Map[String, Set[String]],
    sbtEventsHandler: ReportingSbtTestEventHandler,
    useSbtUi: Boolean // TODO: fix "sbt Test framework quits unexpectedly" when using UI SCL-16240
  ): Future[Boolean] = {
    val testRunCommands: Seq[String] = {
      val projectId = SettingQueryHandler.getProjectIdPrefix(SbtUtil.getSbtProjectIdSeparated(module))
      val commandsRaw  = sbtSupport.commandsBuilder.buildTestOnly(suitesToTestsMap)
      commandsRaw.map(command => s"$projectId testOnly $command")
    }

    val communication = SbtShellCommunication.forProject(module.getProject)

    implicit val ec:
      ExecutionContext = sbtSupport.executionContext

    def modifySettings: Future[Option[SettingMap]] =
      if (useSbtUi)
        for {
          _ <- sbtSupport.initialize(communication)
          mod <- sbtSupport.modifySbtSettingsForUi(module, communication)
        } yield Some(mod)
      else
        Future.successful(None)

    def evaluateCommand(command: String): Future[Unit] =
      communication.command(command, (), SbtShellCommunication.listenerAggregator(sbtEventsHandler.processEvent))

    def evaluateCommands: Future[Seq[Unit]] =
      Future.sequence(testRunCommands.map(evaluateCommand))

    def restoreSettingsFuture(oldSettings: Option[SettingMap]) =
      oldSettings
        .map(sbtSupport.resetSbtSettingsForUi(communication, _))
        .getOrElse(Future.successful(true))

    for {
      oldSettings <- modifySettings
      _           <- evaluateCommands
      success     <- restoreSettingsFuture(oldSettings)
    } yield success
  }
}
