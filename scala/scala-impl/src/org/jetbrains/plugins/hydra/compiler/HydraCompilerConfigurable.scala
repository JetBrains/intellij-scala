package org.jetbrains.plugins.hydra.compiler

import javax.swing.JPanel

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications
import org.jetbrains.plugins.hydra.settings.HydraApplicationSettings
import org.jetbrains.plugins.scala.project.AbstractConfigurable

/**
  * @author Maris Alexandru
  */
class HydraCompilerConfigurable (project: Project) extends AbstractConfigurable("Hydra Compiler"){
  private val settings = HydraCompilerSettings.getInstance(project)
  private val hydraGlobalSettings = HydraApplicationSettings.getInstance()
  private val form = new ScalaHydraCompilerConfigurationPanel(project, settings, hydraGlobalSettings)

  override def createComponent(): JPanel = form.getContentPanel

  override def isModified: Boolean = form.isHydraEnabled != settings.isHydraEnabled ||
    form.getUsername != HydraCredentialsManager.getLogin ||
    form.getPassword != HydraCredentialsManager.getPlainPassword ||
    form.getHydraVersion != settings.hydraVersion ||
    form.selectedNoOfCores != settings.noOfCores ||
    form.selectedSourcePartitioner != settings.sourcePartitioner ||
    form.getHydraRepository != hydraGlobalSettings.getHydraRepositoryUrl ||
    form.getHydraRepositoryRealm != hydraGlobalSettings.hydraRepositoryRealm

  override def reset() {
    form.setUsername(HydraCredentialsManager.getLogin)
    form.setPassword(HydraCredentialsManager.getPlainPassword)
    form.setIsHydraEnabled(settings.isHydraEnabled)
    form.setSelectedNoOfCores(settings.noOfCores)
    form.setHydraVersion(settings.hydraVersion)
    form.setSelectedSourcePartitioner(settings.sourcePartitioner)
    form.setHydraRepository(hydraGlobalSettings.getHydraRepositoryUrl)
    form.setHydraRepositoryRealm(hydraGlobalSettings.hydraRepositoryRealm)
  }

  override def apply() {
    BuildManager.getInstance().clearState(project)
    settings.hydraVersion = form.getHydraVersion
    settings.isHydraEnabled = form.isHydraEnabled
    settings.noOfCores = form.selectedNoOfCores
    settings.sourcePartitioner = form.selectedSourcePartitioner
    hydraGlobalSettings.setHydraRepositopryUrl(form.getHydraRepository)
    hydraGlobalSettings.hydraRepositoryRealm = form.getHydraRepositoryRealm
    HydraCredentialsManager.setCredentials(form.getUsername, form.getPassword)
    EditorNotifications.updateAll()
  }
}