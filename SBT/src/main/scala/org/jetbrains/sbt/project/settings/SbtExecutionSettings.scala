package org.jetbrains.sbt
package project.settings

import java.io.File
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings

/**
 * @author Pavel Fatin
 */
class SbtExecutionSettings(val vmOptions: Seq[String],
                           val customLauncher: Option[File],
                           val customVm: Option[File],
                           val resolveClassifiers: Boolean,
                           val resolveSbtClassifiers: Boolean) extends ExternalSystemExecutionSettings