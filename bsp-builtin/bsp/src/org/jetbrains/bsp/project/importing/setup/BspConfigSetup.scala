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
  // Since config setup run logic is executed using a progress indicator, there is practically
  // no need for an explicit #cancel method, as cancellation should be handled through the indicator.
  // Currently, it is only used by `FastpassConfigSetup`, which is going to be removed in SCL-24892.
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
   * Type representing the preferred tool to use during BSP config regeneration.
   *
   * Effectively works only for Scala CLI, which has two installation kinds (Bundled and Standalone)
   * that produce different connection files (`scala.json` and `scala-cli.json` respectively).
   * During regeneration, the existing connection file name is resolved to a [[ConnectionTarget]], so that [[installCommand]]
   * tries to use the same tool to avoid creating an additional file e.g., `scala.json`, when `scala-cli.json` is already present.
   * If the preferred tool is not available (e.g., the `scala.json` file is present but `scala` command is missing),
   * the installation falls back to any available tool, which may produce a different connection file.
   */
  protected type ConnectionTarget

  /**
   * Maps a BSP connection file name (e.g., `scala.json`) to the corresponding [[ConnectionTarget]]
   * that produces it.
   *
   * @return the resolved target, or `None` if the file name is not recognized.
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

  override def run(indicator: ProgressIndicator, targetConnectionFileName: Option[String])(implicit reporter: BuildReporter): Try[BuildMessages] =
    try {
      currentIndicator = Some(indicator)
      val target = targetConnectionFileName.flatMap(resolveConnectionTarget)
      bspInstall(workspace, indicator, target)
    } finally {
      currentIndicator = None
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
        val warningId = EventId(s"warning:${UUID.randomUUID()}")
        reporter.startTask(warningId, Some(dumpTaskId), msg)
        reporter.finishTask(warningId, msg, new FailureResultImpl())
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
          (Some(exc.getMessage), new SkippedResultImpl(), BuildMessages.Error)
      },
      _ => (None, new SuccessResultImpl(true), BuildMessages.OK)
    )
    finishInstallTask(errorMsg, eventResult, buildMessages)
  }
}
