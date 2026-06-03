package org.jetbrains.sbt.project.settings

import com.intellij.openapi.components.{PersistentStateComponent, State, Storage, StoragePathMacros}
import com.intellij.openapi.module.Module
import com.intellij.util.xmlb.XmlSerializerUtil

import scala.compiletime.uninitialized

@State(
  name = "DisplayModuleName",
  storages = Array(new Storage(StoragePathMacros.MODULE_FILE))
)
class DisplayModuleName extends PersistentStateComponent[DisplayModuleName] {
  private var _name: String = uninitialized
  def name: String = _name

  def setName(name: String): Unit =
    this._name = name
  def getName: String =
    this._name

  override def getState: DisplayModuleName = this

  override def loadState(state: DisplayModuleName): Unit = XmlSerializerUtil.copyBean(state, this)
}

object DisplayModuleName {
  def getInstance(module: Module): DisplayModuleName =
    module.getService(classOf[DisplayModuleName])
}
