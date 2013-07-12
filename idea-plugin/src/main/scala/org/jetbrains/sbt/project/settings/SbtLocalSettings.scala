package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.components._
import project.SbtProjectSystem

/**
 * @author Pavel Fatin
 */
@State(
  name = "SbtLocalSettings",
  storages = Array(
    new Storage(file = StoragePathMacros.WORKSPACE_FILE)
  )
)
class SbtLocalSettings(project: Project)
  extends AbstractExternalSystemLocalSettings(SbtProjectSystem.Id, project)
  with PersistentStateComponent[AbstractExternalSystemLocalSettings.State] {

  def getState = {
    val state = new AbstractExternalSystemLocalSettings.State
    fillState(state)
    state
  }
}

object SbtLocalSettings {
  def getInstance(project: Project) = ServiceManager.getService(project, classOf[SbtLocalSettings])
}