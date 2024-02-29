package org.jetbrains.plugins.scala.bsp.server.connection

import ch.epfl.scala.bsp4j.BspConnectionDetails
import com.intellij.modcommand.ModCommand.error
import com.intellij.openapi.components.{PersistentStateComponent, Service, State, Storage, StoragePathMacros}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlin.coroutines.Continuation
import org.jetbrains.bsp.utils.ParsersKt
import org.jetbrains.plugins.bsp.extension.points.BuildToolId
import org.jetbrains.plugins.bsp.server.connection.ConnectionDetailsProviderExtension
import org.jetbrains.plugins.scala.bsp.config.ScalaPluginConstants.BUILD_TOOL_ID

import java.lang
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._

class ScalaConnectionDetailsProvider extends ConnectionDetailsProviderExtension {
  override def onFirstOpening(project: Project, projectPath: VirtualFile, continuation: Continuation[_ >: lang.Boolean]): AnyRef = {
    saveProjectRootFile(project, projectPath)
    val connectionFile = getConnectionFile(projectPath)
    if (connectionFile.isDefined) {
      return boolean2Boolean(true)
    }

    val hasGeneratedFile = generateSbtConnectionFile(projectPath).isDefined
    boolean2Boolean(hasGeneratedFile)
  }

  override def provideNewConnectionDetails(project: Project, bspConnectionDetails: BspConnectionDetails): BspConnectionDetails = {
    if (bspConnectionDetails != null) {
      return bspConnectionDetails
    }

    val projectRootPath = getProjectRootFile(project) match {
      case Some(projectFile) => projectFile
      case None =>
        error("Cannot obtain project path, please reimport the project.")
        return bspConnectionDetails
    }

    val connectionFile = getConnectionFile(projectRootPath) match {
      case None => generateSbtConnectionFile(projectRootPath)
      case other => other
    }

    connectionFile match {
      case None => null
      case Some(connectionFile) => parseConnectionFile(connectionFile).orNull
    }
  }


  override def getBuildToolId: BuildToolId = BUILD_TOOL_ID

  private def parseConnectionFile(connectionFile: VirtualFile): Option[BspConnectionDetails] =
    Option(ParsersKt.parseBspConnectionDetails(connectionFile))

  private def saveProjectRootFile(project: Project, projectRootFile: VirtualFile): Unit =
    project.getActualComponentManager.getService(classOf[ScalaConnectionDetailsProviderService])
      .loadState(new ScalaConnectionDetailsProviderMetadata(Option(projectRootFile)))

  private def getProjectRootFile(project: Project): Option[VirtualFile] = {
    project.getActualComponentManager.getService(classOf[ScalaConnectionDetailsProviderService])
      .getState
      .projectRootPath
  }

  private def getConnectionFile(projectPath: VirtualFile): Option[VirtualFile] = {
    getChild(projectPath, List.apply(".bsp", "sbt.json"))
  }

  private def generateSbtConnectionFile(projectPath: VirtualFile): Option[VirtualFile] = {
    val process = new ProcessBuilder("coursier", "launch", "sbt", "--", "bspConfig")
      .directory(projectPath.toNioPath.toFile)
      .start

    if (process.waitFor(2, TimeUnit.MINUTES)) {
      if (process.exitValue() != 0) {
        val processInput = process.inputReader.lines.toList.asScala.mkString
        val processError = process.errorReader.lines.toList.asScala.mkString
        throw new RuntimeException(s"Sbt bsp file genration with coursier failed. stdout=[$processInput], stderr=[$processError]")
      }

      getConnectionFile(projectPath)
    } else {
      throw new RuntimeException("Sbt bsp file generation timed out")
    }
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
}

class ScalaConnectionDetailsProviderMetadata(var projectRootPath: Option[VirtualFile])

@State(
  name = "ScalaConnectionDetailsProviderService",
  storages = Array(new Storage(StoragePathMacros.WORKSPACE_FILE)),
  reportStatistic = true
)
@Service(Array(Service.Level.PROJECT))
final class ScalaConnectionDetailsProviderService extends PersistentStateComponent[ScalaConnectionDetailsProviderMetadata] {
  private var projectRootPath: Option[VirtualFile] = Option.empty

  override def getState: ScalaConnectionDetailsProviderMetadata =
    new ScalaConnectionDetailsProviderMetadata(projectRootPath)

  override def loadState(state: ScalaConnectionDetailsProviderMetadata): Unit =
    this.projectRootPath = state.projectRootPath
}
