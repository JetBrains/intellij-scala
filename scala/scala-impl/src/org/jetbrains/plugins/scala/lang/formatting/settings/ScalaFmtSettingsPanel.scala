package org.jetbrains.plugins.scala.lang.formatting.settings

import java.awt.Insets
import java.io.File

import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.openapi.application.{ApplicationManager, ModalityState}
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{TextFieldWithBrowseButton, VerticalFlowLayout}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.components.{JBCheckBox, JBTextField}
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager, Spacer}
import javax.swing.{JComponent, JLabel, JPanel}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaFmtConfigUtil
import org.jetbrains.plugins.scala.extensions._

import scala.collection.mutable

class ScalaFmtSettingsPanel(val settings: CodeStyleSettings) extends CodeStyleAbstractPanel(settings) {
  override def getRightMargin: Int = 0

  protected override def getTabTitle: String = "Scalafmt"

  override def createHighlighter(editorColorsScheme: EditorColorsScheme): EditorHighlighter = null

  override def getFileType: FileType = ScalaFileType.INSTANCE

  override def getPreviewText: String = ""

  private val notifiedPaths = mutable.Set[String]()

  override def apply(codeStyleSettings: CodeStyleSettings): Unit = {
    val scalaCodeStyleSettings = codeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val oldPath = scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH
    scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH = externalFormatterSettingsPath.getText
    scalaCodeStyleSettings.SHOW_SCALAFMT_INVALID_CODE_WARNINGS = showScalaFmtInvalidCodeWarnings.isSelected
    if (!notifiedPaths.contains(scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH) && scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH != "") {
      project.foreach(ScalaFmtConfigUtil.notifyNotSupportedFeatures(scalaCodeStyleSettings, _))
      notifiedPaths.add(scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH)
    }
    displayOrHideConfig(scalaCodeStyleSettings)
    val editorText = getEditor.getDocument.getText
    if (oldPath == scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH && configText.exists(_ != editorText)) {
      getConfigVfile(scalaCodeStyleSettings).foreach{
        vFile =>
          val document = inReadAction(FileDocumentManager.getInstance().getDocument(vFile))
          inWriteAction(ApplicationManager.getApplication.invokeAndWait(document.setText(editorText)), ModalityState.current())
      }
    }
  }

  override def isModified(codeStyleSettings: CodeStyleSettings): Boolean = {
    val scalaCodeStyleSettings = codeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH != externalFormatterSettingsPath.getText ||
      scalaCodeStyleSettings.SHOW_SCALAFMT_INVALID_CODE_WARNINGS != showScalaFmtInvalidCodeWarnings.isSelected ||
      configText.exists(_ != getEditor.getDocument.getText)
  }

  override def resetImpl(codeStyleSettings: CodeStyleSettings): Unit = {
    val scalaCodeStyleSettings = codeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    externalFormatterSettingsPath.setText(scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH)
    showScalaFmtInvalidCodeWarnings.setSelected(scalaCodeStyleSettings.SHOW_SCALAFMT_INVALID_CODE_WARNINGS)
    configText = getConfigVfile(scalaCodeStyleSettings).map { FileDocumentManager.getInstance().getDocument(_).getText }
    configText.foreach(text => inWriteAction(getEditor.getDocument.setText(text)))
    displayOrHideConfig(scalaCodeStyleSettings)
    externalFormatterSettingsPath.getButton.grabFocus()
  }

  override def getPanel: JComponent = {
    if (myPanel == null) {
      myPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true))
      val inner = new JPanel(new GridLayoutManager(3, 3, new Insets(10, 10, 10, 10), -1, -1))
      inner.add(new JLabel("Configuration:"),
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
          GridConstraints.SIZEPOLICY_FIXED,
          GridConstraints.SIZEPOLICY_FIXED, null, null,
          null, 0, false))
      val myTextField = new JBTextField
      myTextField.getEmptyText.setText(s"Default: .${File.separatorChar}${ScalaFmtConfigUtil.defaultConfigurationFileName}")
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
      showScalaFmtInvalidCodeWarnings = new JBCheckBox("Show warnings when trying to format invalid code")
      inner.add(showScalaFmtInvalidCodeWarnings, new GridConstraints(1, 0, 1, 3,
        GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_FIXED,
        GridConstraints.SIZEPOLICY_FIXED, null, null,
        null, 0, false))
      configEditorPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 10, true, true))
      configEditorPanel.add(new JLabel("Configuration content:"))
      val previewPanel = new JPanel()
      configEditorPanel.add(previewPanel)
      installPreviewPanel(previewPanel)
      getEditor.getComponent.setPreferredSize(configEditorPanel.getPreferredSize)
      inner.add(configEditorPanel, new GridConstraints(2, 0, 1, 3,
        GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
        null, 0, false))
      myPanel.add(inner)
    }
    myPanel
  }

  def onProjectSet(aProject: Project): Unit = {
    project = Some(aProject)
    ScalaFmtConfigUtil.notifyNotSupportedFeatures(settings.getCustomSettings(classOf[ScalaCodeStyleSettings]), aProject)
    resetImpl(settings)
  }

  private def displayOrHideConfig(settings: ScalaCodeStyleSettings): Unit = {
    configEditorPanel.setVisible(configText.isDefined)
  }

  private def storeConfig(settings: ScalaCodeStyleSettings): Unit = {
    getConfigVfile(settings)
  }

  private def getConfigVfile(scalaSettings: ScalaCodeStyleSettings): Option[VirtualFile] =
    project.flatMap(ScalaFmtConfigUtil.scalaFmtConfigFile(scalaSettings, _))

  private var project: Option[Project] = None
  private var configText: Option[String] = None
  private var configEditorPanel: JPanel = _
  private var myPanel: JPanel = _
  private var externalFormatterSettingsPath: TextFieldWithBrowseButton = _
  private var showScalaFmtInvalidCodeWarnings: JBCheckBox = _
  private val customSettingsTitle = "Select custom scalafmt configuration file"
}
