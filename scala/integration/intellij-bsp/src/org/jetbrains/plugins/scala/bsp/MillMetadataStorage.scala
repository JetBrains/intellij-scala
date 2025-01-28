package org.jetbrains.plugins.scala.bsp

import com.intellij.openapi.components.{PersistentStateComponent, Service, State, Storage, StoragePathMacros}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

import scala.beans.BeanProperty

@State(
  name = "ScalaBspMetadataStorage",
  storages = Array(new Storage(StoragePathMacros.WORKSPACE_FILE))
)
@Service(Array(Service.Level.PROJECT))
final class MillMetadataStorage extends PersistentStateComponent[MillMetadataStorage] {

  @BeanProperty
  var projectFilePath: Option[VirtualFile] = None

  override def getState: MillMetadataStorage = this

  override def loadState(state: MillMetadataStorage): Unit = {
    projectFilePath = state.projectFilePath
  }
}

object MillMetadataStorage {
  def getInstance(project: Project): MillMetadataStorage = project.getService(classOf[MillMetadataStorage])
}
