package org.jetbrains.plugins.scala.testingSupport.test.sbt

import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.SettingMap
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.shell.{SbtShellCommunication, SettingQueryHandler}
import org.jetbrains.sbt.shell.communication.{SbtShellCommandEventProcessor, SbtShellCommandRequest}

import scala.concurrent.{ExecutionContext, Future}

@ApiStatus.Internal
object SbtShellTestsRunner {

  def runTestsInSbtShell(
    sbtSupport: SbtTestRunningSupport,
    module: Module,
    suitesToTestsMap: Map[String, Set[String]],
    shellEventProcessor: SbtShellCommandEventProcessor[Unit],
    useSbtUi: Boolean // TODO: fix "sbt Test framework quits unexpectedly" when using UI SCL-16240
  ): Future[Boolean] = {
    val testRunCommands: Seq[String] = {
      val projectUriWithId = SbtUtil.getSbtProjectUriAndId(module)
      val projectIdPrefix = projectUriWithId.map(SettingQueryHandler.getProjectIdPrefix).getOrElse("")
      val commandsRaw  = sbtSupport.commandsBuilder.buildTestOnly(suitesToTestsMap)
      commandsRaw.map(command => s"$projectIdPrefix testOnly $command")
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

    def evaluateCommand(command: String): Future[Unit] = {
      val request = SbtShellCommandRequest(command, shellEventProcessor)
      communication.run(request)
    }

    def evaluateCommands: Future[Seq[Unit]] =
      if (useSbtUi && communication.isShuttingDownOrOff)
        Future.successful(Seq.empty)
      else
        Future.sequence(testRunCommands.map(evaluateCommand))


    def restoreSettings(oldSettings: Option[SettingMap]): Future[Boolean] =
      oldSettings match {
        case Some(settings) if !communication.isShuttingDownOrOff =>
          sbtSupport.resetSbtSettingsForUi(communication, settings)
        case _ =>
          Future.successful(true)
      }

    for {
      oldSettings <- modifySettings
      _           <- evaluateCommands
      success     <- restoreSettings(oldSettings)
    } yield success
  }
}
