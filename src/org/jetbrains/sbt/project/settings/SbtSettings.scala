package org.jetbrains.sbt
package project.settings

import java.util

import com.intellij.openapi.components._
import com.intellij.openapi.externalSystem.settings.{AbstractExternalSystemSettings, ExternalSystemSettingsListener}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.ContainerUtilRt
import com.intellij.util.xmlb.annotations.AbstractCollection

import scala.collection.JavaConverters._

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
    state
  }

  def loadState(state: SbtSettingsState) {
    super[AbstractExternalSystemSettings].loadState(state)
  }

  def subscribe(listener: ExternalSystemSettingsListener[SbtProjectSettings]) {
    val adapter = new SbtSettingsListenerAdapter(listener)
    getProject.getMessageBus.connect(getProject).subscribe(SbtTopic, adapter)
  }

  def copyExtraSettingsFrom(settings: SbtSettings) {
  }

  def getLinkedProjectSettings(module: Module): SbtProjectSettings = {
    val linkedSettings = getLinkedProjectsSettings.asScala
    linkedSettings.foldLeft(null.asInstanceOf[SbtProjectSettings]) { (acc, settings) =>
      // TODO: This is a workaround based on assumption that IDEA module's .iml file will
      // always be located in the same dir where linked project's build.sbt file is.
      // What we really need is a way of tracking linked projects which will
      // allow retrieval of their settings using Module object, because linked projects
      // are IDEA's modules after all.
      // It's either our bug or External system's missing feature.
      // @dancingrobot84
      if (settings.getModules.asScala.exists { m => FileUtil.isAncestor(m, module.getModuleFilePath, false) })
        settings
      else
        acc
    }
  }
}

object SbtSettings {
  def getInstance(project: Project) = ServiceManager.getService(project, classOf[SbtSettings])
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
