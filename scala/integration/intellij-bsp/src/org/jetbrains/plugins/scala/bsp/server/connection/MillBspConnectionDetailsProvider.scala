package org.jetbrains.plugins.scala.bsp.server.connection

import ch.epfl.scala.bsp4j.BspConnectionDetails
import com.intellij.modcommand.ModCommand.error
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import org.jetbrains.bsp.protocol.utils.ParsersKt
import org.jetbrains.plugins.bsp.config.BuildToolId
import org.jetbrains.plugins.bsp.impl.server.connection.ConnectionDetailsProviderExtensionJavaShim
import org.jetbrains.plugins.scala.bsp.{MillBspBundle, MillMetadataStorage}
import org.jetbrains.plugins.scala.bsp.config.MillBspPluginConstants.{BSP_CONNECTION_DIR, BSP_CONNECTION_FILE, BUILD_TOOL_ID}

import java.lang
import java.net.URL
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.jdk.CollectionConverters._
import scala.sys.process.{ProcessLogger, stringSeqToProcess}


class MillBspConnectionDetailsProvider extends ConnectionDetailsProviderExtensionJavaShim {
  private val connectionFileRelativePath = s"$BSP_CONNECTION_DIR/$BSP_CONNECTION_FILE"

  override def onFirstOpening(project: Project, projectPath: VirtualFile): CompletableFuture[lang.Boolean] = {
    MillMetadataStorage.getInstance(project).setProjectFilePath(projectPath.getUrl)

    projectPath.findChild(BSP_CONNECTION_DIR) match {
      case null => generateConnectionFile(projectPath)
      case _ => CompletableFuture.completedFuture(true)
    }
  }

  override def provideNewConnectionDetails(project: Project, currentDetails: BspConnectionDetails): BspConnectionDetails = {
    val projectRootPathStr: String = MillMetadataStorage.getInstance(project).getProjectFilePath()
    if (projectRootPathStr == "") {
      error(MillBspBundle.message("mill.root.not.found"))
      return null
    }

    val projectRootPath: VirtualFile = VfsUtil.findFileByURL(new URL(projectRootPathStr)) match {
      case null =>
        error(MillBspBundle.message("mill.root.not.found"))
        return null
      case file => file
    }

    val connectionFile = projectRootPath.findFileByRelativePath(connectionFileRelativePath) match {
      case null =>
        error(MillBspBundle.message("mill.connection.file.not.exist"))
        return null
      case file => file
    }

    val newDetails = ParsersKt.parseBspConnectionDetails(connectionFile)
    val noNewDetails = newDetails != null && newDetails.equals(currentDetails)
    if (noNewDetails) null
    else newDetails
  }

  override def getBuildToolId: BuildToolId = BUILD_TOOL_ID

  private def getMillGenerateBspConnectionFileCommand(projectPath: VirtualFile): Option[List[String]] = {
    getMillExecutableBootstrapFile(projectPath) match {
      case Some(millExecutableFile) =>
        Some(List(
          s"${projectPath.toNioPath.toString}/${millExecutableFile.getName}",
          "mill.bsp.BSP/install"
        ))
      case None =>
        if (!isMillGlobalExecutableAvailable) {
          None
        } else {
          Some(List(
            s"mill",
            "mill.bsp.BSP/install"
          ))
        }
    }
  }

  private def generateConnectionFile(projectPath: VirtualFile): CompletableFuture[lang.Boolean] = {
    CompletableFuture.supplyAsync(() => {
      val processArgs = getMillGenerateBspConnectionFileCommand(projectPath).getOrElse(List.empty)
      if (processArgs.isEmpty) {
        throw new RuntimeException(MillBspBundle.message("mill.executable.not.found"))
      }

      val process = new ProcessBuilder(processArgs.asJava)
        .directory(projectPath.toNioPath.toFile)
        .start()

      process.waitFor(10, TimeUnit.SECONDS)
      if (process.exitValue() != 0) {
        val processInput = process.inputReader.lines.toList.asScala.mkString
        val processError = process.errorReader.lines.toList.asScala.mkString
        throw new RuntimeException(MillBspBundle.message("mill.connection.file.generation.failed", processInput, processError))
      }

      projectPath.refresh(false, false)
      true
    })
  }

  private def isMillGlobalExecutableAvailable: Boolean = {
    try {
      val exitCode = Seq("mill", "--version").!(ProcessLogger(_ => ()))
      exitCode == 0
    } catch {
      case _: Exception => false
    }
  }

  private def getMillExecutableBootstrapFile(projectPath: VirtualFile): Option[VirtualFile] = {
    val fileName = if (SystemInfo.isWindows) "mill.bat" else "mill"
    Option(projectPath.findChild(fileName))
  }
}
