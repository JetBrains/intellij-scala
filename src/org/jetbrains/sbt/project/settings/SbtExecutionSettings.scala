package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings

/**
 * @author Pavel Fatin
 */
class SbtExecutionSettings(project: Project, linkedProjectPath: String) extends ExternalSystemExecutionSettings