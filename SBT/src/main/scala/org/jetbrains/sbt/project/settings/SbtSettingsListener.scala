package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener

/**
 * @author Pavel Fatin
 */
trait SbtSettingsListener extends ExternalSystemSettingsListener[SbtProjectSettings] {
  def onJdkChanged(oldValue: String, newValue: String)

  def onResolveClassifiersChanged(oldValue: Boolean, newValue: Boolean)

  def onResolveSbtClassifiersChanged(oldValue: Boolean, newValue: Boolean)
}