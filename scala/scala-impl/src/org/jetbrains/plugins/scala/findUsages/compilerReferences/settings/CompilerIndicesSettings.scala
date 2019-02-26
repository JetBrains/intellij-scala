package org.jetbrains.plugins.scala.findUsages.compilerReferences.settings

import com.intellij.openapi.components.{PersistentStateComponent, ServiceManager, State, Storage}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.findUsages.compilerReferences.{ScalaCompilerReferenceService, settings}

import scala.beans.BooleanBeanProperty

@State(
  name     = "CompilerIndicesSettings",
  storages = Array(new Storage("compiler_indices_settings.xml"))
)
class CompilerIndicesSettings(project: Project) extends PersistentStateComponent[CompilerIndicesSettings.State] {
  private[this] var state: CompilerIndicesSettings.State = new settings.CompilerIndicesSettings.State

  def isIndexingEnabled: Boolean                   = state.isIndexingEnabled
  def isEnabledForImplicitDefs: Boolean            = state.isEnabledForImplicitDefs
  def isEnabledForApplyUnapply: Boolean            = state.isEnabledForApplyUnapply
  def isEnabledForSAMTypes: Boolean                = state.isEnabledForSAMTypes
  def isEnabledForForComprehensionMethods: Boolean = state.isEnabledForForCompMethods

  def setEnabledForImplicitDefs(enabled:            Boolean): Unit = state.setEnabledForImplicitDefs(enabled)
  def setEnabledForApplyUnapply(enabled:            Boolean): Unit = state.setEnabledForApplyUnapply(enabled)
  def setEnabledForSAMTypes(enabled:                Boolean): Unit = state.setEnabledForSAMTypes(enabled)
  def setEnabledForForComprehensionMethods(enabled: Boolean): Unit = state.setEnabledForForCompMethods(enabled)

  def setIndexingEnabled(v: Boolean): Unit = {
    if (state.indexingEnabled != v) ScalaCompilerReferenceService(project).invalidateIndex()
    state.indexingEnabled = v
  }

  override def getState: CompilerIndicesSettings.State                = state
  override def loadState(loaded: CompilerIndicesSettings.State): Unit = state = loaded
}

object CompilerIndicesSettings {
  class State {
    @BooleanBeanProperty var indexingEnabled: Boolean          = true
    @BooleanBeanProperty var enabledForImplicitDefs: Boolean   = true
    @BooleanBeanProperty var enabledForApplyUnapply: Boolean   = true
    @BooleanBeanProperty var enabledForSAMTypes: Boolean       = true
    @BooleanBeanProperty var enabledForForCompMethods: Boolean = true
  }

  def apply(project: Project): CompilerIndicesSettings =
    ServiceManager.getService(project, classOf[CompilerIndicesSettings])
}
