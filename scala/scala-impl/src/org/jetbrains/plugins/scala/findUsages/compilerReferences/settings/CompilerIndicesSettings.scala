package org.jetbrains.plugins.scala.findUsages.compilerReferences.settings

import com.intellij.openapi.components.{PersistentStateComponent, ServiceManager, State, Storage}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.findUsages.compilerReferences.ScalaCompilerReferenceService

import scala.beans.BeanProperty

@State(
  name     = "CompilerIndicesSettings",
  storages = Array(new Storage("compiler_indices.xml"))
)
class CompilerIndicesSettings(project: Project) extends PersistentStateComponent[CompilerIndicesSettings.State] {
  private[this] var state = new CompilerIndicesSettings.State()

  def indexingEnabled: Boolean = state.indexingEnabled

  def indexingEnabled_=(enabled: Boolean): Unit = {
    if (state.indexingEnabled != enabled) ScalaCompilerReferenceService(project).invalidateIndex()
    state.indexingEnabled = enabled
  }

  override def getState: CompilerIndicesSettings.State               = state
  override def loadState(state: CompilerIndicesSettings.State): Unit = this.state = state
}

object CompilerIndicesSettings {
  class State {
    @BeanProperty var indexingEnabled: Boolean = false
  }

  def apply(project: Project): CompilerIndicesSettings =
    ServiceManager.getService(project, classOf[CompilerIndicesSettings])
}
