package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.settings.{ExternalSystemSettingsListener, AbstractExternalSystemSettings}
import com.intellij.openapi.project.Project
import com.intellij.openapi.components._
import com.intellij.util.containers.ContainerUtilRt
import com.intellij.util.xmlb.annotations.AbstractCollection
import java.util

/**
 * @author Pavel Fatin
 */

@State (
  name = "ScalaSbtSettings",
  storages = Array(
    new Storage(file = StoragePathMacros.PROJECT_FILE),
    new Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/sbt.xml", scheme = StorageScheme.DIRECTORY_BASED)
  )
)
class ScalaSbtSettings(project: Project)
  extends AbstractExternalSystemSettings[ScalaSbtSettings, SbtProjectSettings, SbtSettingsListener](SbtTopic, project)
  with PersistentStateComponent[SbtSettingsState]{

  def checkSettings(old: SbtProjectSettings, current: SbtProjectSettings) {}

  def getState = {
    val state = new SbtSettingsState()
    fillState(state)
    state
  }

  def loadState(state: SbtSettingsState) {
    super[AbstractExternalSystemSettings].loadState(state)
  }

  def subscribe(listener: ExternalSystemSettingsListener[SbtProjectSettings]) {
    val adapter = new SbtSettingsListenerAdapter(listener)
    getProject.getMessageBus.connect(getProject).subscribe(SbtTopic, adapter)
  }

  def copyExtraSettingsFrom(settings: ScalaSbtSettings) {}
}

object ScalaSbtSettings {
  def getInstance(project: Project) = ServiceManager.getService(project, classOf[ScalaSbtSettings])
}

class SbtSettingsState extends AbstractExternalSystemSettings.State[SbtProjectSettings] {
  private val projectSettings = ContainerUtilRt.newTreeSet[SbtProjectSettings]()

  @AbstractCollection(surroundWithTag = false, elementTypes = Array(classOf[SbtProjectSettings]))
  def getLinkedExternalProjectsSettings: util.Set[SbtProjectSettings] = {
    projectSettings
  }

  def setLinkedExternalProjectsSettings(settings: util.Set[SbtProjectSettings]) {
    if (settings != null) {
      projectSettings.addAll(settings)
    }
  }
}
