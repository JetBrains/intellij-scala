package org.jetbrains.plugins.cbt.runner

import javax.swing.JComponent

import com.intellij.openapi.options.SettingsEditor

class CbtRunConfigurationEditor extends SettingsEditor[CbtRunConfiguration]{
  val form = new CbtRunConfigurationForm()

  override def createEditor(): JComponent = form.getMainPanel

  override def resetEditorFrom(configuration: CbtRunConfiguration): Unit = form(configuration)

  override def applyEditorTo(configuration: CbtRunConfiguration): Unit = configuration(form)
}
