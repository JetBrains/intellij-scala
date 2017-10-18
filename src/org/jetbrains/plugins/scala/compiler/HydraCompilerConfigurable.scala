package org.jetbrains.plugins.scala.compiler

import javax.swing.JPanel

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.AbstractConfigurable
import org.jetbrains.plugins.scala.settings.HydraApplicationSettings

/**
  * @author Maris Alexandru
  */
class HydraCompilerConfigurable (project: Project, settings: HydraCompilerSettings, hydraGlobalSettings: HydraApplicationSettings) extends AbstractConfigurable("Hydra Compiler"){
  private val form = new ScalaHydraCompilerConfigurationPanel(project, settings, hydraGlobalSettings)

  override def createComponent(): JPanel = form.getContentPanel

  override def isModified: Boolean = form.isHydraEnabled != settings.isHydraEnabled ||
    form.getUsername != HydraCredentialsManager.getLogin ||
    form.getPassword != HydraCredentialsManager.getPlainPassword ||
    form.selectedVersion != settings.hydraVersion ||
    form.selectedNoOfCores != settings.noOfCores ||
    form.selectedSourcePartitioner != settings.sourcePartitioner ||
    form.getHydraStoreDirectory != settings.hydraStorePath

  override def reset() {
    form.setUsername(HydraCredentialsManager.getLogin)
    form.setPassword(HydraCredentialsManager.getPlainPassword)
    form.setIsHydraEnabled(settings.isHydraEnabled)
    form.setSelectedNoOfCores(settings.noOfCores)
    form.setSelectedVersion(settings.hydraVersion)
    form.setSelectedSourcePartitioner(settings.sourcePartitioner)
    form.setHydraStoreDirectory(settings.hydraStorePath)
  }

  override def apply() {
    BuildManager.getInstance().clearState(project)
    settings.hydraVersion = form.selectedVersion
    settings.isHydraEnabled = form.isHydraEnabled
    settings.noOfCores = form.selectedNoOfCores
    settings.sourcePartitioner = form.selectedSourcePartitioner
    settings.hydraStorePath = form.getHydraStoreDirectory
    HydraCredentialsManager.setCredentials(form.getUsername, form.getPassword)
  }
}