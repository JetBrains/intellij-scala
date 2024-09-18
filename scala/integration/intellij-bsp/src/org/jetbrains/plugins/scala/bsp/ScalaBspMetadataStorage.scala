package org.jetbrains.plugins.scala.bsp

import com.intellij.openapi.components.{PersistentStateComponent, Service, State, Storage, StoragePathMacros}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}

import java.io.File
import scala.beans.BeanProperty

@State(
  name = "ScalaBspMetadataStorage",
  storages = Array[Storage](new Storage(StoragePathMacros.WORKSPACE_FILE))
)
@Service(Array(Service.Level.PROJECT))
final class ScalaBspMetadataStorage extends PersistentStateComponent[ScalaBspMetadataStorage.State] {
  private var state = new ScalaBspMetadataStorage.State

  override def getState: ScalaBspMetadataStorage.State = this.state

  override def loadState(state: ScalaBspMetadataStorage.State): Unit = {
    this.state = state
  }
}

object ScalaBspMetadataStorage {
  def apply(project: Project): ScalaBspMetadataStorage =
    project.getService(classOf[ScalaBspMetadataStorage])

  class State {
    @BeanProperty
    var projectFilePathRaw: String = ""

    def getProjectRootFile: Option[VirtualFile] = {
      if (this.projectFilePathRaw.equals("")) {
        return None
      }

      Some(LocalFileSystem.getInstance().findFileByIoFile(
        new File(this.projectFilePathRaw)
      ))
    }

    def saveProjectRootFile(projectRoot: VirtualFile): Unit = this.projectFilePathRaw = projectRoot.getPath
  }
}
