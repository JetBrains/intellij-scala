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
      Future.sequence(testRunCommands.map(evaluateCommand))

    def restoreSettingsFuture(oldSettings: Option[SettingMap]) =
      oldSettings
        .map(sbtSupport.resetSbtSettingsForUi(communication, _))
        .getOrElse(Future.successful(true))

    // Skip steps that send commands to the sbt shell if it is shutting down or already off.
    // Session-scoped settings (set via `set` commands) are lost on shell termination,
    // so evaluating commands or resetting settings on a dead shell is pointless and would trigger an unwanted shell restart.
    def whenShellAlive[A](default: => A)(body: => Future[A]): Future[A] =
      if (communication.isShuttingDownOrOff) Future.successful(default)
      else body

    for {
      oldSettings <- modifySettings
      _           <- whenShellAlive(Seq.empty[Unit])(evaluateCommands)
      success     <- whenShellAlive(true)(restoreSettingsFuture(oldSettings))
    } yield success
  }
}
