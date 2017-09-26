package org.jetbrains.plugins.cbt.project.settings

import java.awt.{GridLayout, Label}
import javax.swing.JPanel

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton

class CbtSettingsPane {
  val pane = new JPanel
  private val cbtPathText = new TextFieldWithBrowseButton
  cbtPathText.addBrowseFolderListener(
    "Choose a CBT path",
    "Choose a CBT path",
    null,
    FileChooserDescriptorFactory.createSingleFolderDescriptor)

  pane.setLayout(new GridLayout(1, 2))
  pane.add(new Label("CBT path"))
  pane.add(cbtPathText)

  def cbtPath: String =
    cbtPathText.getText

  def updateCbtPath(path: String): Unit =
    cbtPathText.setText(path)
}
