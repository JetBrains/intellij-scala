package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.settings.{ExternalSystemSettingsListener, DelegatingExternalSystemSettingsListener}

/**
 * @author Pavel Fatin
 */
class SbtSettingsListenerAdapter(listener: ExternalSystemSettingsListener[SbtProjectSettings])
  extends DelegatingExternalSystemSettingsListener[SbtProjectSettings](listener) with SbtSettingsListener
