package org.jetbrains.sbt.project.settings

import com.intellij.openapi.externalSystem.settings.{DelegatingExternalSystemSettingsListener, ExternalSystemSettingsListener}

/**
 * Stub to satisfy scaffolding of ExternalSystem
 * @author Pavel Fatin
 */
class SbtProjectSettingsListenerAdapter(listener: ExternalSystemSettingsListener[SbtProjectSettings])
  extends DelegatingExternalSystemSettingsListener[SbtProjectSettings](listener) with SbtProjectSettingsListener
