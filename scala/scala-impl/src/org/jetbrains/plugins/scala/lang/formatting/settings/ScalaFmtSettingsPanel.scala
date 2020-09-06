package org.jetbrains.plugins.scala
package lang
package formatting
package settings

import java.awt._
import java.awt.event.{FocusEvent, FocusListener}
import java.io.File
import java.util.Collections.emptyList

import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.editor.{EditorFactory, EditorSettings}
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory.getSyntaxHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui._
import com.intellij.openapi.ui.popup.{Balloon, JBPopupFactory}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.{JBCheckBox, JBTextField}
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager, Spacer}
import javax.swing._
import javax.swing.event.ChangeEvent
import org.apache.commons.lang.StringUtils
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService.ConfigResolveError
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService.ConfigResolveError.{ConfigError, ConfigScalafmtResolveError}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.{DefaultVersion, ScalafmtVersion}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtNotifications.FmtVerbosity
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.utils.ScalafmtConfigUtils
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.{ScalafmtDynamicConfigService, ScalafmtDynamicConfigServiceImpl, ScalafmtDynamicService}

final class ScalaFmtSettingsPanel(settings: CodeStyleSettings) extends ScalaCodeStylePanelBase(settings, "Scalafmt") {

  import ScalaFmtSettingsPanel._

  private val Log = Logger.getInstance(getClass)

  override val getEditor: EditorEx = createConfigEditor

  override def dispose(): Unit = {
    super.dispose()
    if (getEditor != null) {
      EditorFactory.getInstance().releaseEditor(getEditor)
    }
  }

  override def apply(settings: CodeStyleSettings): Unit = {
    val editorText = getEditor.getDocument.getText
    val configTextChangedInEditor = configText.exists(_ != editorText)

    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val panelWasJustEnabled = isPanelEnabled != scalaSettings.USE_SCALAFMT_FORMATTER

    val modified = isModified(settings) || configTextChangedInEditor || panelWasJustEnabled
    if (!modified) return

    scalaSettings.SCALAFMT_SHOW_INVALID_CODE_WARNINGS = showScalaFmtInvalidCodeWarnings.isSelected
    scalaSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT = useIntellijFormatterForRangeFormat.isSelected
    scalaSettings.SCALAFMT_REFORMAT_ON_FILES_SAVE = reformatOnFileSaveCheckBox.isSelected
    scalaSettings.SCALAFMT_FALLBACK_TO_DEFAULT_SETTINGS = fallBackToDefaultSettings.isSelected

    val configPath = scalaSettings.SCALAFMT_CONFIG_PATH.trim
    val configPathNew = configPathFromUi
    val configPathChanged = configPath != configPathNew

    projectConfigFile(configPathNew) match {
      case Some(vFile) =>
        if (configTextChangedInEditor) {
          saveConfigChangesToFile(editorText, vFile)
        }

        if (configPathChanged || panelWasJustEnabled) {
          scalaSettings.SCALAFMT_CONFIG_PATH = configPathNew
          updateConfigTextFromFile(vFile)
        }
        ensureScalafmtResolved(vFile)
      case None =>
        if (configPathChanged || configTextChangedInEditor) {
          if (configPathNew.isEmpty) {
            scalaSettings.SCALAFMT_CONFIG_PATH = configPathNew
            configText = None
          } else {
            reportConfigFileNotFound(configPathNew)
          }
        }
        ensureDefaultScalafmtResolved()
    }

    isPanelEnabled = scalaSettings.USE_SCALAFMT_FORMATTER
    updateConfigVisibility()
    updateUseIntellijWarningVisibility(scalaSettings)
  }

  private def configPathFromUi: ScalafmtVersion =
    externalFormatterSettingsPath.getText.trim

  //noinspection HardCodedStringLiteral
  private def updateScalafmtVersionLabel(version: String, isDefault: Boolean = false): Unit = {
    if (scalafmtVersionLabel == null) return
    scalafmtVersionLabel.setText(version + (if (isDefault) s" (${ScalaBundle.message("scalafmt.default")})" else ""))
  }

  private def ensureScalafmtResolved(configFile: VirtualFile): Unit = {
    val project = projectOpt match {
      case Some(value) => value
      case None => return
    }

    val versionOpt = ScalafmtDynamicConfigServiceImpl.readVersion(project, configFile) match {
      case Right(v) => v
      case Left(ex) =>
        reportConfigParseError(ex.getMessage)
        return
    }
    val version = versionOpt.getOrElse(DefaultVersion)

    ScalafmtDynamicConfigService.instanceIn(project).toOption match {
      case Some(configManager) =>
        configManager.resolveConfigAsync(configFile, version, FmtVerbosity.Silent, onResolveFinished = {
          case Right(config) =>
            updateScalafmtVersionLabel(config.version, isDefault = versionOpt.isEmpty)
          case Left(error: ConfigError) =>
            updateScalafmtVersionLabel(version, isDefault = versionOpt.isEmpty)
            reportConfigResolveError(error)
          case Left(error: ConfigResolveError) =>
            updateScalafmtVersionLabel("")
            reportConfigResolveError(error)
        })
      case _ =>
        Log.assertTrue(project.isDefault, "Config manager is expected to be missing only in default projects")
        ScalafmtDynamicService.instance.resolveAsync(version, project, {
          case Right(scalaFmtReflect) =>
            updateScalafmtVersionLabel(scalaFmtReflect.version, isDefault = versionOpt.isEmpty)
          case Left(error: ScalafmtDynamicService.ScalafmtResolveError) =>
            updateScalafmtVersionLabel("")
            reportConfigResolveError(ConfigScalafmtResolveError(error))
        })
    }
  }

  private def ensureDefaultScalafmtResolved(): Unit = {
    if (projectOpt.isEmpty) return
    ScalafmtDynamicService.instance.resolveAsync(DefaultVersion, projectOpt.get)
    updateScalafmtVersionLabel(DefaultVersion, isDefault = true)
  }

  private def reportConfigResolveError(configResolveError: ConfigResolveError): Unit = {
    import ConfigResolveError._
    configResolveError match {
      case ConfigFileNotFound(configPath)      => reportConfigFileNotFound(configPath)
      case ConfigParseError(_, exception)      => reportConfigParseError(exception.getMessage)
      case ConfigCyclicDependenciesError(_, _) => reportConfigParseError(ScalaBundle.message("scalafmt.config.load.errors.cyclic.includes.detected"))
      case UnknownError(message, _)            => reportConfigParseError(message)
      case ConfigScalafmtResolveError(error)   => reportCantResolveVersion(error.version)
    }
  }

  private def reportConfigParseError(errorMessage: String): Unit = {
    val component = previewPanel
    displayMessage(
      ScalaBundle.message("scalafmt.settings.panel.errors.failed.to.parse.config", errorMessage),
      component, component.getWidth - 10, component.getHeight - 55, Balloon.Position.above,
      MessageType.WARNING
    )
  }

  private def reportCantResolveVersion(version: ScalafmtVersion): Unit = {
    val component = previewPanel
    displayMessage(
      ScalaBundle.message("scalafmt.settings.panel.errors.cant.resolve.version", version),
      component, component.getWidth - 10, component.getHeight - 55, Balloon.Position.above,
      MessageType.WARNING
    )
  }

  private def reportConfigFileNotFound(configPath: String): Unit = {
    val component = externalFormatterSettingsPath
    displayMessage(
      ScalaBundle.message("scalafmt.settings.panel.errors.cant.find.config.file", configPath),
      component, component.getWidth / 2, component.getHeight, Balloon.Position.below,
      MessageType.ERROR
    )
  }

  private def displayMessage(@Nls text: String, relativeTo: JComponent,
                             xPosition: Int, yPosition: Int,
                             direction: Balloon.Position,
                             messageType: MessageType): Unit = {
    val factory = JBPopupFactory.getInstance.createHtmlTextBalloonBuilder(text, messageType, null)
    val balloon = factory.createBalloon()
    val balloonPosition = new RelativePoint(relativeTo, new Point(xPosition, yPosition))
    balloon.show(balloonPosition, direction)
  }

  private def updateConfigTextFromFile(vFile: VirtualFile): Unit = {
    configText = inReadAction(FileDocumentManager.getInstance.getDocument(vFile).toOption.map(_.getText))
    configText.foreach(text => inWriteAction {
      getEditor.getDocument.setText(text)
    })
  }

  private def saveConfigChangesToFile(configTextNew: String, vFile: VirtualFile): Unit = {
    val document = inReadAction(FileDocumentManager.getInstance.getDocument(vFile))
    inWriteAction {
      invokeAndWait(ModalityState.current()) {
        document.setText(configTextNew)
      }
    }
    configText = Some(configTextNew)
  }

  override def isModified(settings: CodeStyleSettings): Boolean = {
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaSettings.SCALAFMT_CONFIG_PATH.trim != configPathFromUi ||
      scalaSettings.SCALAFMT_SHOW_INVALID_CODE_WARNINGS != showScalaFmtInvalidCodeWarnings.isSelected ||
      scalaSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT != useIntellijFormatterForRangeFormat.isSelected ||
      scalaSettings.SCALAFMT_REFORMAT_ON_FILES_SAVE != reformatOnFileSaveCheckBox.isSelected ||
      scalaSettings.SCALAFMT_FALLBACK_TO_DEFAULT_SETTINGS != fallBackToDefaultSettings.isSelected ||
      configText.exists(_ != getEditor.getDocument.getText)
  }

  override def resetImpl(codeStyleSettings: CodeStyleSettings): Unit = {
    val scalaSettings = codeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val configPath = scalaSettings.SCALAFMT_CONFIG_PATH.trim

    externalFormatterSettingsPath.setText(configPath)
    showScalaFmtInvalidCodeWarnings.setSelected(scalaSettings.SCALAFMT_SHOW_INVALID_CODE_WARNINGS)
    useIntellijFormatterForRangeFormat.setSelected(scalaSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT)
    reformatOnFileSaveCheckBox.setSelected(scalaSettings.SCALAFMT_REFORMAT_ON_FILES_SAVE)
    fallBackToDefaultSettings.setSelected(scalaSettings.SCALAFMT_FALLBACK_TO_DEFAULT_SETTINGS)
    externalFormatterSettingsPath.getButton.grabFocus()

    isPanelEnabled = scalaSettings.USE_SCALAFMT_FORMATTER
    if (isPanelEnabled) {
      projectConfigFile(configPath) match {
        case Some(vFile) =>
          updateConfigTextFromFile(vFile)
          ensureScalafmtResolved(vFile)
        case None =>
          ensureDefaultScalafmtResolved()
      }
    }

    updateConfigVisibility()
    updateUseIntellijWarningVisibility(scalaSettings)
  }

  override def getPanel: JComponent = {
    if (myPanel == null) {
      myPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true))
      myPanel.add(buildInnerPanel)
    }
    myPanel
  }

  private def createNewConfigFileAndUpdate(): Unit =
    projectOpt match {
      case Some(project) =>
        val configPath = ScalafmtConfigUtils.actualConfigPath(configPathFromUi)
        ScalafmtDynamicConfigServiceImpl.createConfigurationFile(project, configPath, openInEditor = true) match {
          case Left(None) =>
          case Left(Some(ex)) =>
            Log.warn("Couldn't create configuration file: " + ex)
          case Right(vFile) =>
            updateConfigTextFromFile(vFile)
            updateConfigVisibility()
        }
      case None =>
    }

  private def buildInnerPanel: JPanel = {
    import GridConstraints._

    def constraint(row: Int, column: Int, rowSpan: Int, colSpan: Int, anchor: Int, fill: Int, HSizePolicy: Int, VSizePolicy: Int) =
      new GridConstraints(row, column, rowSpan, colSpan, anchor, fill, HSizePolicy, VSizePolicy, null, null, null, 0, false)

    val inner = new JPanel(new GridLayoutManager(7, 3, new Insets(10, 15, 10, 15), -1, -1))

    showScalaFmtInvalidCodeWarnings = new JBCheckBox(ScalaBundle.message("scalafmt.settings.panel.show.warnings.when.formatting.invalid.code"))
    useIntellijFormatterForRangeFormat = new JBCheckBox(ScalaBundle.message("scalafmt.settings.panel.use.intellij.formatter.for.code.range.formatting"))
    useIntellijWarning = new JLabel(AllIcons.General.Warning)
    useIntellijWarning.setToolTipText(ScalaBundle.message("scalafmt.settings.panel.use.intellij.formatter.for.code.range.formatting.warning.tooltip"))
    useIntellijFormatterForRangeFormat.addChangeListener((_: ChangeEvent) => {
      useIntellijWarning.setVisible(!useIntellijFormatterForRangeFormat.isSelected)
    })
    val useIntellijFormatterWrapper = {
      val w = new JPanel(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1))
      w.add(useIntellijFormatterForRangeFormat, constraint(0, 0, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
      w.add(useIntellijWarning, constraint(0, 1, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
      w
    }
    reformatOnFileSaveCheckBox = new JBCheckBox(ScalaBundle.message("scalafmt.settings.panel.reformat.on.file.save"))
    fallBackToDefaultSettings = new JBCheckBox(ScalaBundle.message("scalafmt.settings.panel.fallback.to.default.settings"))

    inner.add(showScalaFmtInvalidCodeWarnings,
      constraint(0, 0, 1, 3, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
    inner.add(useIntellijFormatterWrapper,
      constraint(1, 0, 1, 3, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
    inner.add(reformatOnFileSaveCheckBox,
      constraint(2, 0, 1, 3, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
    inner.add(fallBackToDefaultSettings,
      constraint(3, 0, 1, 3, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))

    val configPathTextField = new JBTextField
    //noinspection HardCodedStringLiteral
    configPathTextField.getEmptyText.setText(ScalaBundle.message("scalafmt.default.path", DefaultConfigFilePath))
    externalFormatterSettingsPath = new TextFieldWithBrowseButton(configPathTextField)
    resetConfigBrowserFolderListener()

    //noinspection HardCodedStringLiteral
    inner.add(new JLabel(ScalaBundle.message("scalafmt.settings.panel.configuration.label") + ":"),
      constraint(4, 0, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
    inner.add(externalFormatterSettingsPath,
      constraint(4, 1, 1, 1, ANCHOR_NORTHWEST, FILL_HORIZONTAL, SIZEPOLICY_CAN_GROW | SIZEPOLICY_WANT_GROW, SIZEPOLICY_FIXED))
    inner.add(new Spacer,
      constraint(4, 2, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_WANT_GROW, SIZEPOLICY_CAN_SHRINK))

    scalafmtVersionLabel = new JLabel()
    inner.add(new JLabel(ScalaBundle.message("scalafmt.settings.panel.scalafmt.version.label") + ":"),
      constraint(5, 0, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
    inner.add(scalafmtVersionLabel,
      constraint(5, 1, 1, 1, ANCHOR_NORTHWEST, FILL_HORIZONTAL, SIZEPOLICY_CAN_GROW | SIZEPOLICY_WANT_GROW, SIZEPOLICY_FIXED))
    inner.add(new Spacer,
      constraint(5, 2, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_WANT_GROW, SIZEPOLICY_CAN_SHRINK))

    val configEditorPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 10, true, true))

    noConfigGroup = new JPanel(new HorizontalLayout(10, SwingConstants.CENTER))
    noConfigLabel = new JLabel(ScalaBundle.message("scalafmt.settings.panel.no.config.found.under.path.using.default"))
    noConfigGroup.add(noConfigLabel)
    createConfigButton = new JButton(ScalaBundle.message("scalafmt.can.not.find.config.file.create.new"))
    createConfigButton.addActionListener(_ => createNewConfigFileAndUpdate())
    noConfigGroup.add(createConfigButton)

    configEditorPanel.add(noConfigGroup)

    previewPanel = new JPanel()
    configEditorPanel.add(previewPanel)
    installPreviewPanel(previewPanel)
    getEditor.getComponent.setPreferredSize(configEditorPanel.getPreferredSize)
    inner.add(configEditorPanel,
      constraint(6, 0, 1, 3, ANCHOR_NORTH, FILL_BOTH, SIZEPOLICY_CAN_GROW, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW))

    updateConfigVisibility(configVisible = true)

    inner
  }

  private def resetConfigBrowserFolderListener(): Unit = {
    val button = externalFormatterSettingsPath.getButton
    button.getActionListeners.foreach(button.removeActionListener)

    def updateConfigPath(configPath: String): Unit = {
      projectConfigFile(configPath) match {
        case Some(vFile) =>
          updateConfigTextFromFile(vFile)
          ensureScalafmtResolved(vFile)
        case _ =>
          configText = None
          ensureDefaultScalafmtResolved()
      }
      updateConfigVisibility()
    }

    // if config path text field is empty we want to select default config file in project tree of file browser
    val textAccessor = new TextComponentAccessor[JTextField]() {
      override def setText(textField: JTextField, text: String): Unit = {
        textField.setText(text)
        updateConfigPath(text)
      }
      override def getText(textField: JTextField): String = {
        val path = ScalafmtConfigUtils.actualConfigPath(textField.getText)
        val absolutePath = projectOpt.flatMap(ScalafmtConfigUtils.projectConfigFileAbsolutePath(_, path))
        absolutePath.getOrElse(DefaultConfigFilePath)
      }
    }

    // if we typed only whitespaces and lost focus from text field we should display empty text placeholder
    val focusListener = new FocusListener {
      override def focusGained(e: FocusEvent): Unit = {}
      override def focusLost(e: FocusEvent): Unit = {
        val configPath = configPathFromUi
        if (StringUtils.isBlank(configPath)) {
          externalFormatterSettingsPath.setText(null)
          updateConfigPath(DefaultConfigFilePath)
        } else {
          updateConfigPath(configPath)
        }
      }
    }

    externalFormatterSettingsPath.getTextField.addFocusListener(focusListener)
    externalFormatterSettingsPath.addBrowseFolderListener(
      customSettingsTitle, customSettingsTitle, projectOpt.orNull,
      FileChooserDescriptorFactory.createSingleFileDescriptor("conf"),
      textAccessor
    )
  }

  def onProjectSet(aProject: Project): Unit = {
    projectOpt = Some(aProject)
    resetImpl(settings)
    resetConfigBrowserFolderListener()
  }

  private def updateUseIntellijWarningVisibility(settings: ScalaCodeStyleSettings): Unit = {
    if (useIntellijWarning == null) return
    useIntellijWarning.setVisible(!settings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT)
  }

  private def updateConfigVisibility(): Unit =
    updateConfigVisibility(configText.isDefined)

  private def updateConfigVisibility(configVisible: Boolean): Unit = {
    previewPanel.setVisible(configVisible)
    noConfigGroup.setVisible(!configVisible)
  }

  private def projectConfigFile(configPath: String): Option[VirtualFile] =
    projectOpt.flatMap(ScalafmtConfigUtils.projectConfigFile(_, configPath))

  private var isPanelEnabled: Boolean = false
  private var projectOpt: Option[Project] = None
  private var configText: Option[CharSequence] = None
  private var scalafmtVersionLabel: JLabel = _
  private var noConfigGroup: JComponent = _
  private var noConfigLabel: JLabel = _
  private var createConfigButton: JButton = _
  private var previewPanel: JPanel = _
  private var myPanel: JPanel = _
  private var externalFormatterSettingsPath: TextFieldWithBrowseButton = _
  private var showScalaFmtInvalidCodeWarnings: JBCheckBox = _
  private var useIntellijFormatterForRangeFormat: JBCheckBox = _
  private var useIntellijWarning: JLabel = _
  private var reformatOnFileSaveCheckBox: JBCheckBox = _
  private var fallBackToDefaultSettings: JBCheckBox = _
  private val customSettingsTitle   = ScalaBundle.message("scalafmt.settings.panel.select.custom.scalafmt.configuration.file")
  //noinspection HardCodedStringLiteral
  private val DefaultConfigFilePath = s".${File.separatorChar}${ScalafmtDynamicConfigService.DefaultConfigurationFileName}"
}

object ScalaFmtSettingsPanel {

  private val Log = Logger.getInstance(classOf[ScalaFmtSettingsPanel])

  object SearchFilter {
    // TODO: will not work in non-english localization, also see ScalaCompileServerForm
    def ScalafmtConfiguration = "Scalafmt Configuration"
  }

  //copied from CodeStyleAbstractPanel
  //using non-null getPreviewText breaks setting saving (!!!)
  private def createConfigEditor: EditorEx = {
    val editorFactory = EditorFactory.getInstance
    val editorDocument = editorFactory.createDocument("")
    val editor = editorFactory.createEditor(editorDocument)
      .asInstanceOf[EditorEx]

    val hoconLanguage = Language.findLanguageByID("HOCON")
    fillEditorSettings(editor.getSettings, hoconLanguage)

    if (hoconLanguage != null) {
      val highlighter = new LayeredLexerEditorHighlighter(
        getSyntaxHighlighter(hoconLanguage, null, null),
        new DefaultColorsScheme
      )
      editor.setHighlighter(highlighter)
    }

    editor
  }

  private[this] def fillEditorSettings(settings: EditorSettings,
                                       language: Language): Unit = {
    settings.setLanguageSupplier(() => language)
    settings.setWhitespacesShown(true)
    settings.setLineMarkerAreaShown(false)
    settings.setIndentGuidesShown(false)
    settings.setLineNumbersShown(false)
    settings.setFoldingOutlineShown(false)
    settings.setAdditionalColumnsCount(0)
    settings.setAdditionalLinesCount(1)
    settings.setUseSoftWraps(false)
    settings.setSoftMargins(emptyList[Integer])
  }
}
