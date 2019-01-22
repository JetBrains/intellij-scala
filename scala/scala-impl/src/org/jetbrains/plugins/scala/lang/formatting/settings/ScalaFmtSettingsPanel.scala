package org.jetbrains.plugins.scala.lang.formatting.settings

import java.awt._
import java.io.File
import java.util.Collections.emptyList

import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.{ApplicationManager, ModalityState}
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.{Editor, EditorFactory, EditorSettings}
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.{Balloon, JBPopupFactory}
import com.intellij.openapi.ui.{MessageType, TextFieldWithBrowseButton, VerticalFlowLayout}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.{JBCheckBox, JBTextField}
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager, Spacer}
import javax.swing._
import metaconfig.Configured
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaFmtConfigUtil
import org.scalafmt.config.Config

class ScalaFmtSettingsPanel(val settings: CodeStyleSettings) extends CodeStyleAbstractPanel(settings) {

  override val getEditor: Editor = createConfigEditor

  override def getRightMargin = 0

  protected override def getTabTitle: String = "Scalafmt"

  override def createHighlighter(editorColorsScheme: EditorColorsScheme): EditorHighlighter = null

  override def getFileType: FileType = ScalaFileType.INSTANCE

  override def getPreviewText: String = null


  override def apply(settings: CodeStyleSettings): Unit = {
    val editorText = getEditor.getDocument.getText
    val configTextChangedInEditor = configText.exists(_ != editorText)

    val modified = isModified(settings) || configTextChangedInEditor
    if (!modified) return

    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])

    scalaSettings.SHOW_SCALAFMT_INVALID_CODE_WARNINGS = showScalaFmtInvalidCodeWarnings.isSelected
    scalaSettings.USE_INTELLIJ_FORMATTER_FOR_SCALAFMT_RANGE_FORMAT = useIntellijFormatterForRangeFormat.isSelected

    val configPath = scalaSettings.SCALAFMT_CONFIG_PATH
    val configPathNew = externalFormatterSettingsPath.getText
    val configPathChanged = configPath != configPathNew

    if (configPathChanged) {
      doWithConfigFile(configPathNew) { vFile =>
        scalaSettings.SCALAFMT_CONFIG_PATH = configPathNew // only update config path if the file actually exists
        updateConfigTextFromFile(vFile)
      }
    } else if (configTextChangedInEditor) {
      doWithConfigFile(configPath) { vFile =>
        saveConfigChangesToFile(editorText, vFile)
      }
    }

    updateConfigVisibility()
  }

  private def doWithConfigFile[T](configPath: String)(body: VirtualFile => T): Unit = {
    getConfigVFile(configPath) match {
      case Some(vFile) =>
        body(vFile)
        reportErrorsInConfig()
      case None =>
        reportConfigFileNotFound(configPath)
    }
  }

  private def updateConfigTextFromFile(vFile: VirtualFile): Unit = {
    configText = inReadAction(FileDocumentManager.getInstance.getDocument(vFile).toOption.map(_.getText))
    configText.foreach(text => inWriteAction(getEditor.getDocument.setText(text)))
  }

  private def saveConfigChangesToFile(configTextNew: String, vFile: VirtualFile): Unit = {
    val document = inReadAction(FileDocumentManager.getInstance.getDocument(vFile))
    inWriteAction(ApplicationManager.getApplication.invokeAndWait(document.setText(configTextNew)), ModalityState.current())
    configText = Some(configTextNew)
  }

  override def isModified(codeStyleSettings: CodeStyleSettings): Boolean = {
    val scalaCodeStyleSettings = codeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH != externalFormatterSettingsPath.getText ||
      scalaCodeStyleSettings.SHOW_SCALAFMT_INVALID_CODE_WARNINGS != showScalaFmtInvalidCodeWarnings.isSelected ||
      scalaCodeStyleSettings.USE_INTELLIJ_FORMATTER_FOR_SCALAFMT_RANGE_FORMAT != useIntellijFormatterForRangeFormat.isSelected ||
      configText.exists(_ != getEditor.getDocument.getText)
  }

  override def resetImpl(codeStyleSettings: CodeStyleSettings): Unit = {
    val scalaSettings = codeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val configPath = scalaSettings.SCALAFMT_CONFIG_PATH

    externalFormatterSettingsPath.setText(configPath)
    showScalaFmtInvalidCodeWarnings.setSelected(scalaSettings.SHOW_SCALAFMT_INVALID_CODE_WARNINGS)
    useIntellijFormatterForRangeFormat.setSelected(scalaSettings.USE_INTELLIJ_FORMATTER_FOR_SCALAFMT_RANGE_FORMAT)
    getConfigVFile(configPath).foreach { vFile =>
      updateConfigTextFromFile(vFile)
    }
    updateConfigVisibility()
    externalFormatterSettingsPath.getButton.grabFocus()
  }

  override def getPanel: JComponent = {
    if (myPanel == null) {
      myPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true))
      myPanel.add(buildInnerPanel)
    }
    myPanel
  }

  private def buildInnerPanel: JPanel = {
    import GridConstraints._

    def constraint(row: Int, column: Int, rowSpan: Int, colSpan: Int, anchor: Int, fill: Int, HSizePolicy: Int, VSizePolicy: Int) =
      new GridConstraints(row, column, rowSpan, colSpan, anchor, fill, HSizePolicy, VSizePolicy, null, null, null, 0, false)

    val inner = new JPanel(new GridLayoutManager(4, 3, new Insets(10, 10, 10, 10), -1, -1))

    inner.add(new JLabel("Configuration:"),
      constraint(0, 0, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
    val myTextField = new JBTextField
    myTextField.getEmptyText.setText(s"Default: .${File.separatorChar}${ScalaFmtConfigUtil.defaultConfigurationFileName}")
    externalFormatterSettingsPath = new TextFieldWithBrowseButton(myTextField)
    externalFormatterSettingsPath.addBrowseFolderListener(customSettingsTitle, customSettingsTitle, null,
      FileChooserDescriptorFactory.createSingleFileDescriptor("conf"))
    inner.add(externalFormatterSettingsPath,
      constraint(0, 1, 1, 1, ANCHOR_NORTHWEST, FILL_HORIZONTAL, SIZEPOLICY_CAN_GROW | SIZEPOLICY_WANT_GROW, SIZEPOLICY_FIXED))
    inner.add(new Spacer,
      constraint(0, 2, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_WANT_GROW, SIZEPOLICY_CAN_SHRINK))

    showScalaFmtInvalidCodeWarnings = new JBCheckBox("Show warnings when trying to format invalid code")
    useIntellijFormatterForRangeFormat = new JBCheckBox("Use IntelliJ formatter for code range formatting")
    val useIntellijWarning = new JLabel(AllIcons.General.Warning)
    useIntellijWarning.setToolTipText(
      """Using Scalafmt to format code ranges can lead to  code inconsistencies.
        |Scalafmt is designed to only format entire files with scala code""".stripMargin)

    inner.add(showScalaFmtInvalidCodeWarnings,
      constraint(1, 0, 1, 3, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
    inner.add(useIntellijFormatterForRangeFormat,
      constraint(2, 0, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
    inner.add(useIntellijWarning,
      constraint(2, 1, 1, 2, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))

    val configEditorPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 10, true, true))
    configLabel = new JLabel("Configuration content:")
    noConfigLabel = new JLabel("No Configuration found under specified path")
    configEditorPanel.add(configLabel)
    configEditorPanel.add(noConfigLabel)
    noConfigLabel.setVisible(false)
    previewPanel = new JPanel()
    configEditorPanel.add(previewPanel)
    installPreviewPanel(previewPanel)
    getEditor.getComponent.setPreferredSize(configEditorPanel.getPreferredSize)
    inner.add(configEditorPanel,
      constraint(3, 0, 1, 3, ANCHOR_NORTH, FILL_BOTH, SIZEPOLICY_CAN_GROW, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW))

    inner
  }

  def onProjectSet(aProject: Project): Unit = {
    project = Some(aProject)
    resetImpl(settings)
  }

  private def updateConfigVisibility(): Unit = {
    previewPanel.setVisible(configText.isDefined)
    configLabel.setVisible(configText.isDefined)
    noConfigLabel.setVisible(configText.isEmpty)
  }

  private def getConfigVFile(configPath: String): Option[VirtualFile] =
    project.flatMap(ScalaFmtConfigUtil.scalaFmtConfigFile(configPath, _))

  private def reportErrorsInConfig(): Unit = {
    configText.foreach {
      Config.fromHoconString(_) match {
        case Configured.NotOk(error) =>
          val component = previewPanel
          reportError(s"Failed to parse configuration: <br> ${error.msg}",
            component, component.getWidth - 10, component.getHeight, Balloon.Position.above, MessageType.WARNING)
        case _ =>
      }
    }
  }

  private def reportConfigFileNotFound(configPath: String): Unit = {
    val component = externalFormatterSettingsPath
    reportError(s"Can not find scalafmt config file with path: `$configPath`",
      component, component.getWidth / 2, component.getHeight, Balloon.Position.below)
  }

  private def reportError(text: String, relativeTo: JComponent,
                          xPosition: Int, yPosition: Int,
                          direction: Balloon.Position,
                          messageType: MessageType = MessageType.ERROR): Unit = {
    val factory = JBPopupFactory.getInstance.createHtmlTextBalloonBuilder(text, messageType, null)
    val balloon = factory.createBalloon()
    val balloonPosition = new RelativePoint(relativeTo, new Point(xPosition, yPosition))
    balloon.show(balloonPosition, direction)
  }

  //copied from CodeStyleAbstractPanel
  //using non-null getPreviewText breaks setting saving (!!!)
  private def createConfigEditor: Editor = {
    val editorFactory = EditorFactory.getInstance
    val editorDocument = editorFactory.createDocument("")
    val editor = editorFactory.createEditor(editorDocument).asInstanceOf[EditorEx]
    fillEditorSettings(editor.getSettings)
    editor
  }

  private def fillEditorSettings(editorSettings: EditorSettings): Unit = {
    editorSettings.setWhitespacesShown(true)
    editorSettings.setLineMarkerAreaShown(false)
    editorSettings.setIndentGuidesShown(false)
    editorSettings.setLineNumbersShown(false)
    editorSettings.setFoldingOutlineShown(false)
    editorSettings.setAdditionalColumnsCount(0)
    editorSettings.setAdditionalLinesCount(1)
    editorSettings.setUseSoftWraps(false)
    editorSettings.setSoftMargins(emptyList[Integer])
  }

  private var project: Option[Project] = None
  private var configText: Option[String] = None
  private var configLabel: JLabel = _
  private var noConfigLabel: JLabel = _
  private var previewPanel: JPanel = _
  private var myPanel: JPanel = _
  private var externalFormatterSettingsPath: TextFieldWithBrowseButton = _
  private var showScalaFmtInvalidCodeWarnings: JBCheckBox = _
  private var useIntellijFormatterForRangeFormat: JBCheckBox = _
  private val customSettingsTitle = "Select custom scalafmt configuration file"
}
