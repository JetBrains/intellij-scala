package org.jetbrains.plugins.scala.lang.formatting.settings

import java.awt.Insets
import java.io.File

import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.ui.{TextFieldWithBrowseButton, VerticalFlowLayout}
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.components.JBTextField
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager, Spacer}
import javax.swing.{JComponent, JLabel, JPanel}
import org.jetbrains.plugins.scala.ScalaFileType

class ScalaFmtSettingsPanel(val settings: CodeStyleSettings) extends CodeStyleAbstractPanel(settings) {
  override def getRightMargin: Int = 0

  protected override def getTabTitle: String = "Scalafmt"

  override def createHighlighter(editorColorsScheme: EditorColorsScheme): EditorHighlighter = null

  override def getFileType: FileType = ScalaFileType.INSTANCE

  override def getPreviewText: String = ""

  override def apply(codeStyleSettings: CodeStyleSettings): Unit = {
    val scalaCodeStyleSettings = codeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH = externalFormatterSettingsPath.getText
  }

  override def isModified(codeStyleSettings: CodeStyleSettings): Boolean = {
    val scalaCodeStyleSettings = codeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH != externalFormatterSettingsPath.getText
  }

  override def resetImpl(codeStyleSettings: CodeStyleSettings): Unit = {
    val scalaCodeStyleSettings = codeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    externalFormatterSettingsPath.setText(scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH)
  }


  override def getPanel: JComponent = {
    if (myPanel == null) {
      myPanel = new JPanel(new VerticalFlowLayout())
      val inner = new JPanel(new GridLayoutManager(1, 3, new Insets(10, 10, 10, 10), -1, -1))
      inner.add(new JLabel("Configuration:"),
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
          GridConstraints.SIZEPOLICY_FIXED,
          GridConstraints.SIZEPOLICY_FIXED, null, null,
          null, 0, false))
      val myTextField = new JBTextField
      myTextField.getEmptyText.setText(s"Default: .${File.separatorChar}scalafmt.conf")
      externalFormatterSettingsPath = new TextFieldWithBrowseButton(myTextField)
      externalFormatterSettingsPath.addBrowseFolderListener(customSettingsTitle, customSettingsTitle, null,
        FileChooserDescriptorFactory.createSingleFileDescriptor("conf"))
      inner.add(externalFormatterSettingsPath,
        new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_HORIZONTAL,
          GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW,
          GridConstraints.SIZEPOLICY_FIXED, null, null,
          null, 0, false))
      inner.add(new Spacer, new GridConstraints(0, 2, 1, 1,
        GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK, null, null, null, 0, false))
      myPanel.add(inner)
    }
    myPanel
  }

  private var myPanel: JPanel = _
  private var externalFormatterSettingsPath: TextFieldWithBrowseButton = _
  private val customSettingsTitle = "Select custom scalafmt configuration file"
}
