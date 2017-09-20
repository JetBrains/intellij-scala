package org.jetbrains.plugins.cbt.project.settings

import com.intellij.openapi.externalSystem.settings.{DelegatingExternalSystemSettingsListener, ExternalSystemSettingsListener}

class CbtProjectSettingsListenerAdapter(listener: ExternalSystemSettingsListener[CbtProjectSettings])
  extends DelegatingExternalSystemSettingsListener[CbtProjectSettings](listener)
    with CbtProjectSettingsListener
