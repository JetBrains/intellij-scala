package org.jetbrains.plugins.cbt.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components._

import scala.beans.BeanProperty

@State(
  name = "CbtGlobalSettings",
  storages = Array(new Storage("cbt_global.xml"))
)
class CbtGlobalSettings
  extends ApplicationComponent
    with PersistentStateComponent[CbtGlobalSettingsState] {

  var lastUsedCbtExePath: String = ""

  override def loadState(state: CbtGlobalSettingsState): Unit = {
    lastUsedCbtExePath = state.lastUsedbtExePath
  }

  override def getState: CbtGlobalSettingsState = {
    val state = new CbtGlobalSettingsState
    state.lastUsedbtExePath = lastUsedCbtExePath
    state
  }
}

object CbtGlobalSettings {
  def instance: CbtGlobalSettings =
    ApplicationManager.getApplication.getComponent(classOf[CbtGlobalSettings])
}


class CbtGlobalSettingsState {
  @BeanProperty
  var lastUsedbtExePath: String = ""
}