package org.jetbrains.sbt
package project.settings

import java.util

import com.intellij.openapi.components._
import com.intellij.openapi.externalSystem.settings.{AbstractExternalSystemSettings, ExternalSystemSettingsListener}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.util.containers.ContainerUtilRt
import com.intellij.util.xmlb.annotations.AbstractCollection
import org.jetbrains.annotations.Nullable

import scala.beans.BeanProperty

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
class SbtSettings(project: Project)
  extends AbstractExternalSystemSettings[SbtSettings, SbtProjectSettings, SbtSettingsListener](SbtTopic, project)
  with PersistentStateComponent[SbtSettingsState]{

  @Nullable
  private var _jdk: String = _

  private var _resolveClassifiers: Boolean = false

  private var _resolveSbtClassifiers: Boolean = false

  private var _sbtVersion: String = _

  var sbtSupportSuggested = false

  def jdk: String = _jdk

  def jdk_=(value: String) {
    if (!Comparing.equal(_jdk, value)) {
      val oldValue = _jdk
      _jdk = value
      getPublisher.onJdkChanged(oldValue, value)
    }
  }

  def resolveClassifiers: Boolean = _resolveClassifiers
  
  def resolveClassifiers_=(value: Boolean) {
    if (_resolveClassifiers != value) {
      val oldValue = _resolveClassifiers
      _resolveClassifiers = value
      getPublisher.onResolveClassifiersChanged(oldValue, value)
    }
  }

  def resolveSbtClassifiers: Boolean = _resolveSbtClassifiers

  def resolveSbtClassifiers_=(value: Boolean) {
    if (_resolveSbtClassifiers != value) {
      val oldValue = _resolveSbtClassifiers
      _resolveSbtClassifiers = value
      getPublisher.onResolveSbtClassifiersChanged(oldValue, value)
    }
  }

  def sbtVersion: String = _sbtVersion

  def sbtVersion_=(value: String): Unit = {
    if (!Comparing.equal(_sbtVersion, value)) {
      val oldValue = _sbtVersion
      _sbtVersion = value
      getPublisher.onSbtVersionChanged(oldValue, value)
    }
  }

  def checkSettings(old: SbtProjectSettings, current: SbtProjectSettings) {
    if (old.jdkName != current.jdkName) {
      getPublisher.onJdkChanged(old.jdk, current.jdk)
    }
    if (old.resolveClassifiers != current.resolveClassifiers) {
      getPublisher.onResolveClassifiersChanged(old.resolveClassifiers, current.resolveClassifiers)
    }
    if (old.resolveSbtClassifiers != current.resolveSbtClassifiers) {
      getPublisher.onResolveSbtClassifiersChanged(old.resolveSbtClassifiers, current.resolveSbtClassifiers)
    }
    if (old.sbtVersion != current.sbtVersion) {
      getPublisher.onSbtVersionChanged(old.sbtVersion, current.sbtVersion)
    }
  }

  def getState = {
    val state = new SbtSettingsState()
    fillState(state)
    state.jdk = jdk
    state.resolveClassifiers = resolveClassifiers
    state.resolveSbtClassifiers = resolveSbtClassifiers
    state.sbtVersion = sbtVersion
    state.sbtSupportSuggested = sbtSupportSuggested
    state
  }

  def loadState(state: SbtSettingsState) {
    super[AbstractExternalSystemSettings].loadState(state)
    jdk = state.jdk
    resolveClassifiers = state.resolveClassifiers
    resolveSbtClassifiers = state.resolveSbtClassifiers
    sbtVersion = state.sbtVersion
    sbtSupportSuggested = state.sbtSupportSuggested
  }

  def subscribe(listener: ExternalSystemSettingsListener[SbtProjectSettings]) {
    val adapter = new SbtSettingsListenerAdapter(listener)
    getProject.getMessageBus.connect(getProject).subscribe(SbtTopic, adapter)
  }

  def copyExtraSettingsFrom(settings: SbtSettings) {
    jdk = settings.jdk
    resolveClassifiers = settings.resolveClassifiers
    resolveSbtClassifiers = settings.resolveSbtClassifiers
    sbtVersion = settings.sbtVersion
    sbtSupportSuggested = settings.sbtSupportSuggested
  }
}

object SbtSettings {
  def getInstance(project: Project) = ServiceManager.getService(project, classOf[SbtSettings])
}

class SbtSettingsState extends AbstractExternalSystemSettings.State[SbtProjectSettings] {
  @Nullable
  @BeanProperty
  var jdk: String = _

  @BeanProperty
  var resolveClassifiers: Boolean = false

  @BeanProperty
  var resolveSbtClassifiers: Boolean = false

  @Nullable
  @BeanProperty
  var sbtVersion: String = _

  @BeanProperty
  var sbtSupportSuggested: Boolean = false

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
