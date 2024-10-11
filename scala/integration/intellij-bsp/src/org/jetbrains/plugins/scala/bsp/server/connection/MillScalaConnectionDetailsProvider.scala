package org.jetbrains.plugins.scala.bsp.server.connection

import ch.epfl.scala.bsp4j.BspConnectionDetails
import com.intellij.modcommand.ModCommand.error
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.bsp.protocol.utils.ParsersKt
import org.jetbrains.plugins.bsp.config.BuildToolId
import org.jetbrains.plugins.bsp.impl.server.connection.ConnectionDetailsProviderExtensionJavaShim
import org.jetbrains.plugins.scala.bsp.{MillBspBundle, ScalaBspMetadataStorage}
import org.jetbrains.plugins.scala.bsp.config.MillScalaPluginConstants.BUILD_TOOL_ID

import java.lang
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.jdk.CollectionConverters._


class MillScalaConnectionDetailsProvider extends ConnectionDetailsProviderExtensionJavaShim {
  private def getConnectionFile(projectPath: VirtualFile): Option[VirtualFile] = {
    getChild(projectPath, List.apply(".bsp", "mill-bsp.json"))
  }

  private def getChild(root: VirtualFile, path: List[String]): Option[VirtualFile] = {
    val found = path.foldLeft(Option(root)) { (vf, child) =>
      vf.flatMap(vf => {
        vf.refresh(false, false)
        Option(vf.findChild(child))
      }
      )
    }

    found.map(vf => {
      vf.refresh(false, false)
      vf
    })
  }

  private def generateConnectionFile(projectPath: VirtualFile): CompletableFuture[lang.Boolean] = {

    CompletableFuture.supplyAsync(() => {
      val processArgs = List(
        s"${projectPath.toNioPath.toString}/mill",
        "mill.bsp.BSP/install"
      )

      val process = new ProcessBuilder(processArgs.asJava)
        .directory(projectPath.toNioPath.toFile)
        .start()

      process.waitFor(2, TimeUnit.MINUTES)
      if (process.exitValue() != 0) {
        val processInput = process.inputReader.lines.toList.asScala.mkString
        val processError = process.errorReader.lines.toList.asScala.mkString
        throw new RuntimeException(MillBspBundle.message("mill.connection.file.generation.failed", processInput, processError))
      }

      getConnectionFile(projectPath).isDefined
    })
  }


  override def onFirstOpening(project: Project, projectPath: VirtualFile): CompletableFuture[lang.Boolean] = {
    val metadataStorage = ScalaBspMetadataStorage(project)

    val metadataStorageState = metadataStorage.getState
    metadataStorageState.saveProjectRootFile(projectPath)

    metadataStorage.loadState(metadataStorageState)

    getConnectionFile(projectPath) match {
      case None => generateConnectionFile(projectPath)
      case _ => CompletableFuture.completedFuture(true)
    }
  }

  override def provideNewConnectionDetails(project: Project, currentDetails: BspConnectionDetails): BspConnectionDetails = {
    val maybeProjectRootPath = ScalaBspMetadataStorage(project).getState.getProjectRootFile

    val projectRootPath = maybeProjectRootPath match {
      case Some(projectRootPath) => projectRootPath
      case None =>
        error(MillBspBundle.message("mill.root.not.found"))
        return null
    }

    val connectionFile = getChild(projectRootPath, List.apply(".bsp", "mill-bsp.json")) match {
      case Some(connectionFile) => connectionFile
      case None =>
        error(MillBspBundle.message("mill.connection.file.not.exist"))
        return null
    }

    val newDetails = ParsersKt.parseBspConnectionDetails(connectionFile)
    if (newDetails != null && newDetails.equals(currentDetails)) {
      null
    } else {
      newDetails
    }
  }

  override def getBuildToolId: BuildToolId = BUILD_TOOL_ID
}
