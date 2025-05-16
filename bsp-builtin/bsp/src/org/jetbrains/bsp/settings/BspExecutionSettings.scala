package org.jetbrains.bsp.settings

import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings
import com.intellij.openapi.project.Project
import org.jetbrains.bsp.settings.BspProjectSettings.{AutoConfig, AutoPreImport, BspServerConfig, PreImportConfig}
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class BspExecutionSettings(val basePath: Path,
                           val traceBsp: Boolean,
                           val runPreImportTask: Boolean,
                           val preImportTask: PreImportConfig,
                           val config: BspServerConfig
                          ) extends ExternalSystemExecutionSettings

object BspExecutionSettings {

  def executionSettingsFor(project: Project, basePath: Path): BspExecutionSettings = {
    if (project == null) executionSettingsFor(basePath)
    val bspSettings = BspSettings.getInstance(project)
    val bspTraceLog = BspSystemSettings.getInstance.getState.traceBsp
    val linkedSettings = Option(bspSettings.getLinkedProjectSettings(basePath.toCanonicalPath.toString))
    val runPreImportTask = linkedSettings.forall(_.runPreImportTask)
    val preImportConfig = linkedSettings.map(_.preImportConfig).getOrElse(AutoPreImport)
    val serverConfig = linkedSettings.map(_.serverConfig).getOrElse(AutoConfig)

    new BspExecutionSettings(basePath, bspTraceLog, runPreImportTask, preImportConfig, serverConfig)
  }

  def executionSettingsFor(basePath: Path): BspExecutionSettings = {
    val systemSettings = BspSystemSettings.getInstance
    val defaultProjectSettings = new BspProjectSettings
    new BspExecutionSettings(
      basePath, systemSettings.getState.traceBsp, defaultProjectSettings.runPreImportTask, AutoPreImport, AutoConfig)
  }
}
