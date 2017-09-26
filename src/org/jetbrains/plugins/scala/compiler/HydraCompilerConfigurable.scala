package org.jetbrains.plugins.scala.compiler

import javax.swing.JPanel

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.AbstractConfigurable

/**
  * @author Maris Alexandru
  */
class HydraCompilerConfigurable (project: Project, settings: HydraCompilerSettings) extends AbstractConfigurable("Hydra Compiler"){
  private val form = new ScalaHydraCompilerConfigurationPanel(project, settings)

  override def createComponent(): JPanel = form.getContentPanel

  override def isModified: Boolean = form.isHydraEnabled != settings.isHydraEnabled ||
    form.getUsername != HydraCredentialsManager.getLogin ||
    form.getPassword != HydraCredentialsManager.getPlainPassword

  override def reset() {
    form.setUsername(HydraCredentialsManager.getLogin)
    form.setPassword(HydraCredentialsManager.getPlainPassword)
    form.setIsHydraEnabled(settings.isHydraEnabled)
  }

  override def apply() {
    BuildManager.getInstance().clearState(project)
    settings.isHydraEnabled = form.isHydraEnabled
    HydraCredentialsManager.setCredentials(form.getUsername, form.getPassword)
  }
}