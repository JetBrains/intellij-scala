package org.jetbrains.plugins.scala.findUsages.compilerReferences.settings

import com.intellij.openapi.components.{PersistentStateComponent, ServiceManager, State, Storage}
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.plugins.scala.findUsages.compilerReferences.ScalaCompilerReferenceService

import scala.beans.BooleanBeanProperty

@State(
  name     = "CompilerIndicesSettings",
  storages = Array(new Storage("compiler_indices.xml"))
)
class CompilerIndicesSettings(project: Project) extends PersistentStateComponent[CompilerIndicesSettings] {
  private[this] var indexingEnabled: Boolean                 = true
  @BooleanBeanProperty var enabledForImplicitDefs: Boolean   = true
  @BooleanBeanProperty var enabledForApplyUnapply: Boolean   = true
  @BooleanBeanProperty var enabledForSAMTypes: Boolean       = true
  @BooleanBeanProperty var enabledForForCompMethods: Boolean = true

  def isIndexingEnabled: Boolean = indexingEnabled

  def setIndexingEnabled(v: Boolean): Unit = {
    if (indexingEnabled != v) ScalaCompilerReferenceService(project).invalidateIndex()
    indexingEnabled = v
  }

  override def getState: CompilerIndicesSettings               = this
  override def loadState(state: CompilerIndicesSettings): Unit = XmlSerializerUtil.copyBean(state, this)
}

object CompilerIndicesSettings {
  def apply(project: Project): CompilerIndicesSettings =
    ServiceManager.getService(project, classOf[CompilerIndicesSettings])
}
