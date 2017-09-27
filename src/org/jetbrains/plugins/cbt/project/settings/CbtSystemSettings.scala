package org.jetbrains.plugins.cbt.project.settings

import java.util

import com.intellij.openapi.components.{PersistentStateComponent, ServiceManager, State, Storage}
import com.intellij.openapi.externalSystem.settings.{AbstractExternalSystemSettings, ExternalSystemSettingsListener}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtilRt
import com.intellij.util.xmlb.annotations.AbstractCollection
import org.jetbrains.plugins.cbt.settings.CbtGlobalSettings
import org.jetbrains.sbt.project.settings.SbtProjectSettings

import scala.beans.BeanProperty


@State(
  name = "CbtSettings",
  storages = Array(new Storage("cbt.xml"))
)
class CbtSystemSettings(project: Project)
  extends AbstractExternalSystemSettings[CbtSystemSettings, CbtProjectSettings, CbtProjectSettingsListener](CbtTopic, project)
    with PersistentStateComponent[CbtSystemSettingsState] {

  @BeanProperty
  var cbtExePath: String = CbtGlobalSettings.instance.lastUsedCbtExePath

  override def copyExtraSettingsFrom(settings: CbtSystemSettings): Unit = {}

  override def checkSettings(old: CbtProjectSettings, current: CbtProjectSettings): Unit = {}

  def subscribe(listener: ExternalSystemSettingsListener[CbtProjectSettings]): Unit = {
    val adapter = new CbtProjectSettingsListenerAdapter(listener)
    getProject.getMessageBus.connect(getProject).subscribe(CbtTopic, adapter)
  }

  override def getLinkedProjectSettings(linkedProjectPath: String): CbtProjectSettings =
    Option(super.getLinkedProjectSettings(linkedProjectPath))
      .getOrElse(super.getLinkedProjectSettings(ExternalSystemApiUtil.normalizePath(linkedProjectPath)))

  override def loadState(state: CbtSystemSettingsState): Unit = {
    super[AbstractExternalSystemSettings].loadState(state)
    cbtExePath = state.cbtExePath
  }

  override def getState: CbtSystemSettingsState = {
    val state = new CbtSystemSettingsState()
    fillState(state)
    state.cbtExePath = cbtExePath
    state
  }
}

object CbtSystemSettings {
  def instance(project: Project): CbtSystemSettings = ServiceManager.getService(project, classOf[CbtSystemSettings])
}

class CbtSystemSettingsState extends AbstractExternalSystemSettings.State[CbtProjectSettings] {
  private val projectSettings = ContainerUtilRt.newTreeSet[CbtProjectSettings]()

  @BeanProperty
  var cbtExePath: String = CbtGlobalSettings.instance.lastUsedCbtExePath

  override def getLinkedExternalProjectsSettings: util.Set[CbtProjectSettings] = projectSettings

  @AbstractCollection(surroundWithTag = false, elementTypes = Array(classOf[SbtProjectSettings]))
  def setLinkedExternalProjectsSettings(settings: util.Set[CbtProjectSettings]): Unit = {
    if (settings != null) {
      projectSettings.addAll(settings)
    }
  }
}