package org.jetbrains.bsp.project.importing.setup

import com.intellij.build.events.EventResult
import com.intellij.build.events.impl.{FailureResultImpl, SkippedResultImpl, SuccessResultImpl}
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator}
import org.jetbrains.bsp.{BspBundle, BspUtil}
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}

import java.nio.file.Path
import java.util.UUID
import scala.util.Try

abstract class BspConfigSetup {
  def cancel(): Unit
  def run(indicator: ProgressIndicator)(implicit reporter: BuildReporter): Try[BuildMessages]
}

/**
 * Base class for BSP configuration setups that use simple command-line tools to generate BSP configurations.
 * Provides common logic for running BSP installation commands.
 */
abstract class CommandBasedBspConfigSetup(workspace: Path) extends BspConfigSetup {

  private var currentIndicator: Option[ProgressIndicator] = None

  /** The name of the build server (e.g., "Mill", "Scala CLI"). Used in progress messages. */
  protected def serverName: String

  /** Returns the command to run for BSP installation. */
  protected def installCommand(workspace: Path, indicator: ProgressIndicator): Try[Seq[String]]

  override def cancel(): Unit = {
    currentIndicator.foreach { indicator =>
      if (!indicator.isCanceled)
        indicator.cancel()
    }
    currentIndicator = None
  }

  override def run(indicator: ProgressIndicator)(implicit reporter: BuildReporter): Try[BuildMessages] = {
    currentIndicator = Some(indicator)
    try {
      bspInstall(workspace, indicator)
    } finally {
      currentIndicator = None
    }
  }

  private def bspInstall(workspace: Path, indicator: ProgressIndicator)(implicit reporter: BuildReporter): Try[BuildMessages] = {
    val dumpTaskId = EventId(s"dump:${UUID.randomUUID()}")
    reporter.startTask(dumpTaskId, None, BspBundle.message("bsp.resolver.installing.configuration", serverName))

    val command = installCommand(workspace, indicator)
    val work = command.toEither.flatMap { cmd =>
      reporter.log(BspBundle.message("bsp.resolver.installing.configuration.command", cmd.mkString(" ")))
      BspUtil.runCommand(workspace, indicator, cmd: _*)
    }

    def finishInstallTask(errorMsg: Option[String], result: EventResult, status: BuildMessages.BuildStatus): Try[BuildMessages] = {
      val buildMessages = BuildMessages.empty.status(status)
      errorMsg.filter(_.nonEmpty).foreach { msg =>
        reporter.logErr(msg)
        buildMessages.addError(msg)
      }
      reporter.finishTask(dumpTaskId, BspBundle.message("bsp.resolver.installing.configuration", serverName), result)
      Try(buildMessages)
    }

    val (errorMsg, eventResult, buildMessages) = work.fold(
      {
        case _: ProcessCanceledException =>
          (None, new SkippedResultImpl(), BuildMessages.Canceled)
        case exc =>
          (Some(exc.getMessage), new FailureResultImpl(), BuildMessages.Error)
      },
      _ => (None, new SuccessResultImpl(true), BuildMessages.OK)
    )
    finishInstallTask(errorMsg, eventResult, buildMessages)
  }
}
