package org.jetbrains.sbt
package project.settings

import java.io.File

import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings

/**
 * @author Pavel Fatin
 */
class SbtExecutionSettings(val vmExecutable: File,
                           val vmOptions: Seq[String],
                           val environment: Map[String,String],
                           val customLauncher: Option[File],
                           val customSbtStructureDir: Option[String],
                           val jdk: Option[String],
                           val resolveClassifiers: Boolean,
                           val resolveSbtClassifiers: Boolean) extends ExternalSystemExecutionSettings