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
  /**
   * Since `BspConfigSetup` can be run as part of the import and build process, it’s necessary to keep in mind:
   *  - Proper indicator handling to allow the `BspConfigSetup` task to be canceled
   *  - Starting an additional task or displaying a node via the `BuildReporter` to indicate that the BSP config task is running.
   *    (it currently does not work in the build, only in the sync window)
   */
  def run(indicator: ProgressIndicator)(implicit reporter: BuildReporter): Try[BuildMessages]

  /** If this method is used, it must be ensured that the given config setup supports the target connection file */
  def run(indicator: ProgressIndicator, targetConnectionFileName: Option[String])(implicit reporter: BuildReporter): Try[BuildMessages] =
    run(indicator)
}

/**
 * Base class for BSP configuration setups that use simple command-line tools to generate BSP configurations.
 * Provides common logic for running BSP installation commands.
 */
abstract class CommandBasedBspConfigSetup(workspace: Path) extends BspConfigSetup {

  private var currentIndicator: Option[ProgressIndicator] = None

  /** The name of the build server (e.g., "Mill", "Scala CLI"). Used in progress messages. */
  protected def serverName: String

  /**
   * Subclass-specific type representing the resolved connection target.
   * Effectively works only in the Scala CLI config.
   */
  protected type ConnectionTarget

  /**
   * Maps a target connection file name to a typed [[ConnectionTarget]].
   * Returns `None` if this setup doesn't handle the given file, causing the setup to be skipped.
   */
  protected def resolveConnectionTarget(fileName: String): Option[ConnectionTarget] = None

  /** Returns the command to run for BSP installation. */
  protected def installCommand(workspace: Path, indicator: ProgressIndicator, target: Option[ConnectionTarget]): Try[Seq[String]]

  override def cancel(): Unit = {
    currentIndicator.foreach { indicator =>
      if (!indicator.isCanceled)
        indicator.cancel()
    }
    currentIndicator = None
  }

  override def run(indicator: ProgressIndicator, targetConnectionFileName: Option[String])(implicit reporter: BuildReporter): Try[BuildMessages] = {
    val target = targetConnectionFileName.flatMap { name =>
      resolveConnectionTarget(name) match {
        case resolved @ Some(_) => resolved
        case None => return Try(BuildMessages.empty.status(BuildMessages.OK))
      }
    }

    currentIndicator = Some(indicator)
    try {
      bspInstall(workspace, indicator, target)
    } finally {
      currentIndicator = None
    }
  }

  override def run(indicator: ProgressIndicator)(implicit reporter: BuildReporter): Try[BuildMessages] =
    run(indicator, targetConnectionFileName = None)

  private def bspInstall(workspace: Path, indicator: ProgressIndicator, target: Option[ConnectionTarget])(implicit reporter: BuildReporter): Try[BuildMessages] = {
    val dumpTaskId = EventId(s"dump:${UUID.randomUUID()}")
    reporter.startTask(dumpTaskId, None, BspBundle.message("bsp.resolver.installing.configuration", serverName))

    val command = installCommand(workspace, indicator, target)
    val work = command.toEither.flatMap { cmd =>
      reporter.log(BspBundle.message("bsp.resolver.installing.configuration.command", cmd.mkString(" ")))
      BspUtil.runCommand(workspace, indicator, cmd*)
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
