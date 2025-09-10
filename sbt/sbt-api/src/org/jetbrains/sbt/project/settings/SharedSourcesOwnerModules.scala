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

  /**
   * Keeps the IntelliJ module names of shared sources owner modules. This is necessary instead of just having [[ownersModuleIds]],
   * because in [[org.jetbrains.jps.incremental.scala.sources.SharedSourceDependenciesProviderService]], the representative target
   * from [[org.jetbrains.jps.ModuleChunk]] is represented by the IntelliJ module name.
   *
   * @note the name of this field must be in sync with the name used in [[org.jetbrains.jps.incremental.scala.sources.SharedSourcesModuleSerializer.loadProperties]]
   */
  @BeanProperty
  var ownersModuleNames: JList[String] = _

  /**
   * Keeps the shared sources owner module IDs (the IDs are generated in [[org.jetbrains.sbt.project.data.ModuleNode.combinedId]]).
   * Used to get the shared sources owner modules, which are later used to calculate the JVM/representative module.
   * It is more reliable to use module IDs instead of module names, as the IDs remain unchanged even if the user renames the module.
   */
  @BeanProperty
  var ownersModuleIds: JList[String] = _

  override def getState: SharedSourcesOwnerModules = this

  override def loadState(s: SharedSourcesOwnerModules): Unit = XmlSerializerUtil.copyBean(s, this)
}

object SharedSourcesOwnerModules {
  def getInstance(module: Module): SharedSourcesOwnerModules = {
    module.getService(classOf[SharedSourcesOwnerModules])
  }
}