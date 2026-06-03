package org.jetbrains.sbt.project.settings

import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener

/**
 * A dummy to satisfy interface constraints of ExternalSystem
 */
trait SbtProjectSettingsListener extends ExternalSystemSettingsListener[SbtProjectSettings]
