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
  extends AbstractExternalSystemLocalSettings[SbtLocalSettingsState](SbtProjectSystem.Id, project)
  with PersistentStateComponent[SbtLocalSettingsState] {

  var sbtSupportSuggested = false

  override def getState: SbtLocalSettingsState = {
    val state = new SbtLocalSettingsState
    state.setSbtSupportSuggested(sbtSupportSuggested)
    state
  }

  def loadState(state: SbtLocalSettingsState)  {
    super[AbstractExternalSystemLocalSettings].loadState(state)
    sbtSupportSuggested = state.getSbtSupportSuggested
  }
}

class SbtLocalSettingsState extends AbstractExternalSystemLocalSettings.State {
  @BeanProperty
  var sbtSupportSuggested: Boolean = false
}

object SbtLocalSettings {
  def getInstance(project: Project): SbtLocalSettings = project.getService(classOf[SbtLocalSettings])
}