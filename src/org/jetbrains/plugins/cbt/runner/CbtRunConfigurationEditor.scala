package org.jetbrains.plugins.cbt.runner

import javax.swing.JComponent

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

class CbtRunConfigurationEditor(project: Project, configuration: CbtRunConfiguration)
  extends SettingsEditor[CbtRunConfiguration] {
  val form = new CbtRunConfigurationForm

  override def createEditor(): JComponent = form.getMainPanel

  override def resetEditorFrom(configuration: CbtRunConfiguration): Unit = form(configuration, modules)

  override def applyEditorTo(configuration: CbtRunConfiguration): Unit = configuration(form)

  private def modules: Array[String] =
    ModuleManager.getInstance(project)
      .getSortedModules
      .reverse
      .map(_.getName)
}
