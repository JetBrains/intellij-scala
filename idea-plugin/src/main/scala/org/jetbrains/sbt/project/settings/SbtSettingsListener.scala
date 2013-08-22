package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener

/**
 * @author Pavel Fatin
 */
trait SbtSettingsListener extends ExternalSystemSettingsListener[SbtProjectSettings]