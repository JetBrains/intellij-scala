package org.jetbrains.sbt.project.settings

import com.intellij.openapi.components._
import com.intellij.openapi.module.Module
import com.intellij.util.xmlb.XmlSerializerUtil

import java.util.{List => JList}
import scala.beans.BeanProperty

/**
 * Persistent state component with a module file storage.
 * In shared sources module files, it stores the module names of their owner modules.
 *
 * It is read by [[org.jetbrains.jps.incremental.scala.sources.SharedSourcesModuleSerializer]] on the JPS side.
 *
 * ATTENTION:
 *  - The class name must stay in sync with the one used by `SharedSourcesModuleSerializer`.
 *  - If you rename or change the type of [[ownersModuleNames]], verify that
 *   `SharedSourcesModuleSerializer` still reads it correctly.
 */
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