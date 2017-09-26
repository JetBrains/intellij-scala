package org.jetbrains.plugins.cbt.settings

import com.intellij.openapi.components._
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.project.settings.CbtSystemSettings

import scala.beans.BeanProperty

@State(
  name = "CbtGlobalSettings",
  storages = Array(new Storage("cbt_global.xml"))
)
class CbtGlobalSettings
  extends ApplicationComponent
    with PersistentStateComponent[CbtGlobalSettingsState] {

  var cbtExePath: String = ""

  override def loadState(state: CbtGlobalSettingsState): Unit = {
    cbtExePath = state.cbtExePath
  }

  override def getState: CbtGlobalSettingsState = {
    val state = new CbtGlobalSettingsState
    state.cbtExePath = cbtExePath
    state
  }
}

object CbtGlobalSettings {
  def instance: CbtGlobalSettings = ServiceManager.getService(classOf[CbtGlobalSettings])
}


class CbtGlobalSettingsState {
  @BeanProperty
  var cbtExePath: String = ""
}