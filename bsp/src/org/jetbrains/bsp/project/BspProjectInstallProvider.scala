package org.jetbrains.bsp.project

import com.intellij.build.events.EventResult
import com.intellij.build.events.impl.{FailureResultImpl, SuccessResultImpl}
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.bsp.BspBundle
import org.jetbrains.bsp.project.importing.bspConfigSteps.ConfigSetup
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter, ExternalSystemNotificationReporter}

import java.io.File
import java.util.UUID
import scala.io.Source
import scala.jdk.CollectionConverters.{IteratorHasAsScala, SeqHasAsJava}
import scala.util.{Failure, Success, Try}

//TODO maybe BloopPreImporter should also be included in this logic?
@ApiStatus.Internal
trait BspProjectInstallProvider {

  def canImport(workspace: File): Boolean
  def serverName: String
  def installCommand(workspace: File): Try[Seq[String]]
  def getConfigSetup: ConfigSetup

  def bspInstall(workspace: File)(implicit reporter: BuildReporter): Try[BuildMessages] = {
    val stderr = new StringBuilder

    val dumpTaskId = EventId(s"dump:${UUID.randomUUID()}")
    reporter.startTask(dumpTaskId, None, BspBundle.message("bsp.resolver.installing.mill.configuration", serverName))

    def executeCommand(command: Seq[String]): Try[Int] = {
      reporter.log(BspBundle.message("bsp.resolver.installing.mill.configuration.command", command.mkString(" ")))

      Try {
        val generalCommandLine = new GeneralCommandLine(command.asJava)
          .withWorkDirectory(workspace)
        val process = generalCommandLine.toProcessBuilder.start()
        process.waitFor()

        val stderrText = Source.fromInputStream(process.getErrorStream).mkString.trim
        stderr.append(stderrText)

        process.exitValue()
      }
    }

    val command = installCommand(workspace)
    val work = command.flatMap(executeCommand)

    def finishMillInstallTask(errorMsg: Option[String], result: EventResult, status: BuildMessages.BuildStatus): Try[BuildMessages] = {
      val logError: String => Unit = (msg: String) => reporter match {
        case reporter: ExternalSystemNotificationReporter => reporter.logErr(msg)
        case _ => reporter.log(msg)
      }
      val buildMessages = BuildMessages.empty.status(status)
      errorMsg.filter(_.nonEmpty).foreach { msg =>
        logError(msg)
        buildMessages.addError(msg)
      }
      reporter.finishTask(dumpTaskId, BspBundle.message("bsp.resolver.installing.mill.configuration", serverName), result)
      Try(buildMessages)
    }

    val (errorMsg, eventResult, buildMessages) = work match {
      case Success(0) => (None, new SuccessResultImpl(true), BuildMessages.OK)
      case Success(_) => (Some(stderr.toString()), new FailureResultImpl(), BuildMessages.Error)
      case Failure(exc) => (Some(exc.getMessage), new FailureResultImpl(), BuildMessages.Error)
    }
    finishMillInstallTask(errorMsg, eventResult, buildMessages)
  }

  private def getConfigSetupIfImportable(workspace: File): Option[ConfigSetup] =
    if (canImport(workspace)) Some(getConfigSetup)
    else None
}

object BspProjectInstallProvider {

  private val EP = ExtensionPointName.create[BspProjectInstallProvider]("org.intellij.bsp.bspProjectInstallProvider")

  def canImport(workspace: File): Boolean =
    getImplementations.exists(_.canImport(workspace))

  def getConfigs(workspace: File): Seq[ConfigSetup] =
    getImplementations.flatMap(_.getConfigSetupIfImportable(workspace)).toSeq

  def getImplementations: Iterator[BspProjectInstallProvider] =
    EP.getExtensionList.iterator().asScala
}
