package org.jetbrains.sbt.project.settings

import com.intellij.openapi.components._
import com.intellij.openapi.module.Module
import com.intellij.util.xmlb.XmlSerializerUtil

import java.util.{List => JList}
import scala.beans.BeanProperty

@State(
  name = "SharedSourcesOwnerModules",
  storages = Array(new Storage(StoragePathMacros.MODULE_FILE))
)
class SharedSourcesOwnerModules extends PersistentStateComponent[SharedSourcesOwnerModules] {

  @BeanProperty
  var ownersModuleNames: JList[String] = _

  override def getState: SharedSourcesOwnerModules = this

  override def loadState(s: SharedSourcesOwnerModules): Unit = XmlSerializerUtil.copyBean(s, this)
}

object SharedSourcesOwnerModules {
  def getInstance(module: Module): SharedSourcesOwnerModules = {
    module.getService(classOf[SharedSourcesOwnerModules])
  }
}