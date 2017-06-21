package org.jetbrains.plugins.cbt.project.settings

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.settings.{AbstractExternalSystemSettings, ExternalSystemSettingsListener}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project

class CbtSystemSettings(project: Project)
  extends AbstractExternalSystemSettings[CbtSystemSettings, CbtProjectSettings, CbtProjectSettingsListener](CbtTopic, project) {

  override def copyExtraSettingsFrom(settings: CbtSystemSettings): Unit = {}

  override def checkSettings(old: CbtProjectSettings, current: CbtProjectSettings): Unit = {}

  override def subscribe(listener: ExternalSystemSettingsListener[CbtProjectSettings]): Unit = {}

  override def getLinkedProjectSettings(linkedProjectPath: String): CbtProjectSettings =
    Option(super.getLinkedProjectSettings(linkedProjectPath))
      .getOrElse(super.getLinkedProjectSettings(ExternalSystemApiUtil.normalizePath(linkedProjectPath)))
}

object CbtSystemSettings {
  def getInstance(project: Project): CbtSystemSettings = ServiceManager.getService(project, classOf[CbtSystemSettings])
}
