package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings

import java.io.File

class SbtExecutionSettings(val realProjectPath: String,
                           val vmExecutable: File,
                           val vmOptions: Seq[String],
                           val hiddenDefaultMaxHeapSize: JvmMemorySize,
                           val environment: Map[String,String],
                           val customLauncher: Option[File],
                           val customSbtStructureFile: Option[File],
                           val jdk: Option[String],
                           val resolveClassifiers: Boolean,
                           val resolveJavadocs: Boolean,
                           val resolveSbtClassifiers: Boolean,
                           val useShellForImport: Boolean,
                           val shellDebugMode: Boolean,
                           val allowSbtVersionOverride: Boolean,
                           val preferScala2: Boolean,
                          ) extends ExternalSystemExecutionSettings
