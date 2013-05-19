package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.components._

/**
 * @author Pavel Fatin
 */

@State (
  name = "SbtSettings",
  storages = Array(
    new Storage(file = StoragePathMacros.PROJECT_FILE),
    new Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/sbt.xml", scheme = StorageScheme.DIRECTORY_BASED)
  )
)
class SbtSettings(project: Project) extends AbstractExternalSystemSettings[SbtProjectSettings, SbtSettingsListener](SbtTopic, project) {
  def checkSettings(old: SbtProjectSettings, current: SbtProjectSettings) {}
}

object SbtSettings {
  def getInstance(project: Project) = ServiceManager.getService(project, classOf[SbtSettings])
}