package org.jetbrains.sbt.project.settings

import com.intellij.openapi.components.{PersistentStateComponent, State, Storage, StoragePathMacros}
import com.intellij.util.xmlb.XmlSerializerUtil

import com.intellij.openapi.module.Module
import scala.beans.BeanProperty

@State(
  name = "DisplayModuleName",
  storages = Array(new Storage(StoragePathMacros.MODULE_FILE))
)
class DisplayModuleName extends PersistentStateComponent[DisplayModuleName] {

  @BeanProperty
  var name: String = _

  override def getState: DisplayModuleName = this

  override def loadState(state: DisplayModuleName): Unit = XmlSerializerUtil.copyBean(state, this)
}

object DisplayModuleName {
  def getInstance(module: Module): DisplayModuleName =
    module.getService(classOf[DisplayModuleName])
}
