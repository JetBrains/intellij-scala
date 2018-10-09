package org.jetbrains.plugins.scala.findUsages.compilerReferences

import com.intellij.openapi.components.{PersistentStateComponent, ServiceManager, State, Storage}
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

import scala.beans.BeanProperty

@State(
  name     = "CompilerIndicesSettings",
  storages = Array(new Storage("compiler_indices.xml"))
)
class CompilerIndicesSettings extends PersistentStateComponent[CompilerIndicesSettings] {
  @BeanProperty var classfileIndexingEnabled: Boolean = false

  override def getState: CompilerIndicesSettings               = this
  override def loadState(state: CompilerIndicesSettings): Unit = XmlSerializerUtil.copyBean(state, this)
}

object CompilerIndicesSettings {
  def apply(project: Project): CompilerIndicesSettings =
    ServiceManager.getService(project, classOf[CompilerIndicesSettings])
}
