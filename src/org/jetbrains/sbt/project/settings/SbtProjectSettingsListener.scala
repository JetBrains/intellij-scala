package org.jetbrains.sbt.project.settings

import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener

/**
 * A dummy to satisfy interface constraints of ExternalSystem
 * @author Pavel Fatin
 */
trait SbtProjectSettingsListener extends ExternalSystemSettingsListener[SbtProjectSettings]
