package org.jetbrains.sbt.project.settings

import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener

/**
 * @author Pavel Fatin
 */
trait SbtProjectSettingsListener extends ExternalSystemSettingsListener[SbtProjectSettings] {
  def onJdkChanged(oldValue: String, newValue: String)

  def onResolveClassifiersChanged(oldValue: Boolean, newValue: Boolean)

  def onResolveSbtClassifiersChanged(oldValue: Boolean, newValue: Boolean)

  def onSbtVersionChanged(oldValue: String, newValue: String)
}
