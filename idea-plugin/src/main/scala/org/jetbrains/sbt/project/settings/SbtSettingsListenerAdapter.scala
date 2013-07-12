package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.settings.{ExternalSystemSettingsListener, DelegatingExternalSystemSettingsListener}
import java.util

/**
 * @author Pavel Fatin
 */
class SbtSettingsListenerAdapter(listener: ExternalSystemSettingsListener[SbtProjectSettings])
  extends DelegatingExternalSystemSettingsListener[SbtProjectSettings](listener) with SbtSettingsListener {

  override def onProjectsLinked(settings: util.Collection[SbtProjectSettings]) {
    super.onProjectsLinked(settings)
  }

  override def onUseAutoImportChange(currentValue: Boolean, linkedProjectPath: String) {
    super.onUseAutoImportChange(currentValue, linkedProjectPath)
  }

  override def onBulkChangeStart() {
    super.onBulkChangeStart()
  }

  override def onBulkChangeEnd() {
    super.onBulkChangeEnd()
  }
}
