package org.jetbrains.plugins.cbt.runner

import javax.swing.{JComponent, JPanel}

import com.intellij.openapi.options.SettingsEditor

class CbtRunConfigurationEditor extends SettingsEditor[CbtRunConfiguration]{
  val panel = new JPanel()

  override def createEditor(): JComponent = panel

  override def applyEditorTo(s: CbtRunConfiguration): Unit = {}

  override def resetEditorFrom(s: CbtRunConfiguration): Unit = {}
}
