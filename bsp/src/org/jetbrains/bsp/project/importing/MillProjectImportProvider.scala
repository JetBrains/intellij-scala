package org.jetbrains.bsp.project.importing

import com.intellij.build.events.EventResult
import com.intellij.build.events.impl.{FailureResultImpl, SuccessResultImpl}
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.bsp.BspBundle
import org.jetbrains.plugins.scala.build.BuildMessages.EventId

import java.io.File
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter, ExternalSystemNotificationReporter}

import java.util.UUID
import scala.io.Source
import scala.sys.process._
import scala.util.{Failure, Success, Try, Using}

object MillProjectImportProvider {
  def canImport(workspace: File): Boolean =
    Option(workspace) match {
      case Some(directory) if directory.isDirectory => isBspCompatible(directory) || isLegacyBspCompatible(directory)
      case _ => false
    }

  private val versionPattern = """^.*(0\.8\.0|0\.7.+|0\.6.+)$"""

  def bspInstall(workspace: File)(implicit reporter: BuildReporter): Try[BuildMessages] = {
    val stdout = new StringBuilder
    val stderr = new StringBuilder

    val dumpTaskId = EventId(s"dump:${UUID.randomUUID()}")
    reporter.startTask(dumpTaskId, None, BspBundle.message("bsp.resolver.installing.mill.configuration"))

    def executeMillCommand(millCommand: String): Try[Int] = {
      reporter.log(BspBundle.message("bsp.resolver.installing.mill.configuration.command", millCommand))
      Try(Process(millCommand, workspace)! ProcessLogger(stdout append _ + "\n", stderr append _ + "\n"))
    }

    // note: The legacy part is only executed for mill bootstrap script so it is not applicable for Windows.
    // Maybe it could be, but we decided to support mill.bat file only for the newer bsp approach
    val isLegacyMill = !SystemInfo.isWindows && isLegacyBspCompatible(workspace)
    val millFileOpt = getMillFile(workspace)
    val millCommand = millFileOpt match {
      case Some(file) if isMillFileBspCompatible(file, workspace) =>
          val millFilePath = getMillFilePath(file)
          Success(s"$millFilePath -i mill.bsp.BSP/install")
      case Some(_) if isLegacyMill =>
        Success("./mill -i mill.contrib.BSP/install")
      case _ => Failure(new IllegalStateException("Unable to install BSP as this is not a Mill project"))
    }
    val work = millCommand.flatMap(executeMillCommand)

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
      reporter.finishTask(dumpTaskId, BspBundle.message("bsp.resolver.installing.mill.configuration"), result)
      Try(buildMessages)
    }

    val (errorMsg, eventResult, buildMessages) = work match {
      case Success(0) => (None, new SuccessResultImpl(true), BuildMessages.OK)
      case Success(_) => (Some(stderr.toString()), new FailureResultImpl(), BuildMessages.Error)
      case Failure(exc) => (Some(exc.getMessage), new FailureResultImpl(), BuildMessages.Error)
    }
    finishMillInstallTask(errorMsg, eventResult, buildMessages)
  }

  //the absolute path for bat file is needed to executed correctly on Windows
  private def getMillFilePath(millFile: File): String =
    if (SystemInfo.isWindows) millFile.getAbsolutePath
    else "./mill"

  private def getMillFile(workspace: File): Option[File] =
    if (SystemInfo.isWindows) findFileByName(workspace, "mill.bat")
    else findFileByName(workspace, "mill")

  private def checkMillVersionWithBatFile(file: File, workspace: File): Boolean = {
    val stdout = new StringBuilder
    val versionCommand = s"${file.getAbsolutePath} --version"
    Process(versionCommand, workspace) ! ProcessLogger(stdout append _ + "\n", _ => ())

    stdout.toString()
      .split("\n")
      .exists { line =>
        line.contains("Mill Build Tool version") && !line.matches(versionPattern)
      }
  }

  private def isBspCompatible(workspace: File) = {
    val fileOpt = getMillFile(workspace)
    fileOpt.exists(isMillFileBspCompatible(_, workspace))
  }

  private def isMillFileBspCompatible(millFile: File, workspace: File): Boolean = {
    if (SystemInfo.isWindows) {
      checkMillVersionWithBatFile(millFile, workspace)
    } else {
      Using.resource(Source.fromFile(millFile)) { source =>
        source
          .getLines()
          .exists(t => !t.matches(versionPattern))
      }
    }
  }

  // Legacy Mill =< 0.8.0
  private def isLegacyBspCompatible(workspace: File) =
    findFileByName(workspace, "build.sc").exists { buildScript =>
      Using.resource(Source.fromFile(buildScript))(
        _.getLines().contains("import $ivy.`com.lihaoyi::mill-contrib-bsp:$MILL_VERSION`")
      )
    }

  private def findFileByName(dir: File, name: String): Option[File] =
    Option(dir.listFiles())
      .getOrElse(Array.empty)
      .find(x => x.getName == name && !x.isDirectory)
}
