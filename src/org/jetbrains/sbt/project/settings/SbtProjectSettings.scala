package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings

/**
 * @author Pavel Fatin
 */
class SbtProjectSettings extends ExternalProjectSettings {
  override def clone() = {
    val result = new SbtProjectSettings()
    copyTo(result)
    result
  }
}
