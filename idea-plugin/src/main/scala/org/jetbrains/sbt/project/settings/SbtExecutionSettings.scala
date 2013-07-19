package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings

/**
 * @author Pavel Fatin
 */
class SbtExecutionSettings(val vmOptions: Seq[String]) extends ExternalSystemExecutionSettings