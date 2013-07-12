package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener
import java.util

/**
 * @author Pavel Fatin
 */
trait SbtSettingsListener extends ExternalSystemSettingsListener[SbtProjectSettings] {
  def onProjectsLinked(settings: util.Collection[SbtProjectSettings]) {}

  def onUseAutoImportChange(currentValue: Boolean, linkedProjectPath: String) {}

  def onBulkChangeStart() {}

  def onBulkChangeEnd() {}
}
