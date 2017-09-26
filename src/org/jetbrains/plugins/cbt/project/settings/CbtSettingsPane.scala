package org.jetbrains.plugins.cbt.project.settings

import java.awt.{GridLayout, Label}
import javax.swing.JPanel

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton

class CbtSettingsPane {
  val pane = new JPanel
  private val cbtExePathText = new TextFieldWithBrowseButton
  cbtExePathText.addBrowseFolderListener(
    "Choose a CBT executable path",
    "Choose a CBT executable path",
    null,
    FileChooserDescriptorFactory.createSingleFileDescriptor)

  pane.setLayout(new GridLayout(1, 2))
  pane.add(new Label("CBT executable path"))
  pane.add(cbtExePathText)

  def cbtPath: String =
    cbtExePathText.getText

  def updateCbtPath(path: String): Unit =
    cbtExePathText.setText(path)
}
