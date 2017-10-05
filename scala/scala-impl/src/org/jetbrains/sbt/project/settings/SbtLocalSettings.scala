package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.components._
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.project.SbtProjectSystem

import scala.beans.BeanProperty

/**
 * @author Pavel Fatin
 */
@State(
  name = "SbtLocalSettings",
  storages = Array(new Storage(StoragePathMacros.WORKSPACE_FILE))
)
class SbtLocalSettings(project: Project)
  extends AbstractExternalSystemLocalSettings(SbtProjectSystem.Id, project)
  with PersistentStateComponent[SbtLocalSettingsState] {

  var sbtSupportSuggested = false
  var lastUpdateTimestamp = 0L

  def getState: SbtLocalSettingsState = {
    val state = new SbtLocalSettingsState
    fillState(state)
    state.setSbtSupportSuggested(sbtSupportSuggested)
    state.setLastUpdateTimestamp(lastUpdateTimestamp)
    state
  }

  def loadState(state: SbtLocalSettingsState)  {
    super[AbstractExternalSystemLocalSettings].loadState(state)
    sbtSupportSuggested = state.getSbtSupportSuggested
    lastUpdateTimestamp = state.getLastUpdateTimestamp
  }
}

class SbtLocalSettingsState extends AbstractExternalSystemLocalSettings.State {
  @BeanProperty
  var sbtSupportSuggested: Boolean = false
  @BeanProperty
  var lastUpdateTimestamp: Long = 0
}

object SbtLocalSettings {
  def getInstance(project: Project): SbtLocalSettings = ServiceManager.getService(project, classOf[SbtLocalSettings])
}