package org.jetbrains.plugins.scala.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.{PersistentStateComponent, SettingsCategory, State, Storage}
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.plugins.scala.extensions.BooleanExt

import scala.beans.BeanProperty

@State(
  name     = "CompilerIndicesSbtSettings",
  storages = Array(new Storage("compiler_indices_sbt.xml")),
  reportStatistic = true,
  category = SettingsCategory.TOOLS
)
class CompilerIndicesSbtSettings extends PersistentStateComponent[CompilerIndicesSbtSettings] {
  @BeanProperty var useManualConfiguration: Boolean = false
  @BeanProperty var sbtConnectionPort: Int          = 65337

  def sbtPort: Int = if (useManualConfiguration) sbtConnectionPort else 0

  override def getState: CompilerIndicesSbtSettings               = this
  override def loadState(state: CompilerIndicesSbtSettings): Unit = XmlSerializerUtil.copyBean(state, this)
}

object CompilerIndicesSbtSettings {
  def apply(): CompilerIndicesSbtSettings = ApplicationManager.getApplication.getService(classOf[CompilerIndicesSbtSettings])
}
