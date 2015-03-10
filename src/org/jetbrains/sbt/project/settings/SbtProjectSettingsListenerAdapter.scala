package org.jetbrains.sbt.project.settings

import com.intellij.openapi.externalSystem.settings.{DelegatingExternalSystemSettingsListener, ExternalSystemSettingsListener}

/**
 * @author Pavel Fatin
 */
class SbtProjectSettingsListenerAdapter(listener: ExternalSystemSettingsListener[SbtProjectSettings])
  extends DelegatingExternalSystemSettingsListener[SbtProjectSettings](listener) with SbtProjectSettingsListener {

  def onJdkChanged(oldValue: String, newValue: String) {}

  def onResolveClassifiersChanged(oldValue: Boolean, newValue: Boolean) {}

  def onResolveSbtClassifiersChanged(oldValue: Boolean, newValue: Boolean) {}

  def onSbtVersionChanged(oldValue: String, newValue: String) {}
}
