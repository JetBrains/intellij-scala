package org.jetbrains.bsp.project

import com.intellij.build.events.EventResult
import com.intellij.build.events.impl.{FailureResultImpl, SuccessResultImpl}
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.bsp.project.importing.bspConfigSteps.ConfigSetup
import org.jetbrains.bsp.{BspBundle, BspUtil}
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter, ExternalSystemNotificationReporter}

import java.nio.file.Path
import java.util.UUID
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.util.Try

//TODO maybe BloopPreImporter should also be included in this logic?
@ApiStatus.Internal
trait BspProjectInstallProvider {

  def canImport(workspace: Path): Boolean
  def serverName: String
  def installCommand(workspace: Path): Try[Seq[String]]
  def getConfigSetup: ConfigSetup

  def bspInstall(workspace: Path)(implicit reporter: BuildReporter): Try[BuildMessages] = {
    val dumpTaskId = EventId(s"dump:${UUID.randomUUID()}")
    reporter.startTask(dumpTaskId, None, BspBundle.message("bsp.resolver.installing.configuration", serverName))

    val command = installCommand(workspace)
    val work = command.toEither.flatMap { cmd =>
      reporter.log(BspBundle.message("bsp.resolver.installing.configuration.command", cmd.mkString(" ")))
      BspUtil.runCommand(workspace, cmd:_*)
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
      exc => (Some(exc.getMessage), new FailureResultImpl(), BuildMessages.Error),
      _ => (None, new SuccessResultImpl(true), BuildMessages.OK)
    )
    finishInstallTask(errorMsg, eventResult, buildMessages)
  }

  private def getConfigSetupIfImportable(workspace: Path): Option[ConfigSetup] =
    if (canImport(workspace)) Some(getConfigSetup)
    else None
}

object BspProjectInstallProvider {

  private val EP = ExtensionPointName.create[BspProjectInstallProvider]("org.intellij.bsp.bspProjectInstallProvider")

  def canImport(workspace: Path): Boolean =
    getImplementations.exists(_.canImport(workspace))

  def getConfigs(workspace: Path): Seq[ConfigSetup] =
    getImplementations.flatMap(_.getConfigSetupIfImportable(workspace)).toSeq

  def getImplementations: Iterator[BspProjectInstallProvider] =
    EP.getExtensionList.iterator().asScala
}
