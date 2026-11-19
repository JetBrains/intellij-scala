package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.{ModalityState, ReadAction, WriteIntentReadAction}
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.editor.{EditorFactory, EditorSettings}
import com.intellij.openapi.fileChooser.{FileChooserDescriptor, FileChooserDescriptorFactory}
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory.getSyntaxHighlighter
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.openapi.ui._
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.ui.popup.{Balloon, JBPopupFactory}
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.{JBCheckBox, JBTextField}
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager, Spacer}
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.{RequiresBackgroundThread, RequiresEdt}
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.formatting.ScalaFormattingBundle
import org.jetbrains.plugins.scala.settings.ScalaSettingsBundle
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService.ConfigResolveError
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService.ConfigResolveError.{ConfigError, ConfigScalafmtResolveError}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.utils.ScalafmtConfigUtils
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.{ScalafmtDynamicConfigService, ScalafmtDynamicConfigServiceImpl}
import org.jetbrains.plugins.scala.lang.formatting.settings.EdtRequestTracker.Request
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalafmtSettingsConfigResolver.{Completion, Failure, LoadedConfig, Result, Version}
import org.scalafmt.dynamic.ScalafmtVersion

import java.awt._
import java.awt.event.{ActionEvent, FocusAdapter, FocusEvent}
import java.util.Collections.emptyList
import javax.swing._
import javax.swing.event.ChangeEvent
import scala.annotation.nowarn

/**
 * Edits Scalafmt settings and previews the selected configuration.
 *
 * Supplying the resolver separately keeps formatter resolution independent of UI updates.
 * `ScalaFmtSettingsPanelResolutionTest` uses a controlled resolver to choose completion
 * order and thread deterministically, without depending on formatter downloads, caches
 * or background-task timing. The UI, project files, VFS and documents remain real.
 * The public single-argument constructor supplies the production resolver.
 *
 * @param settings initial code style settings for this panel
 * @param configResolver loads configurations and resolves formatter versions on a worker thread.
 *                       The panel dispatches UI updates to EDT and ignores stale completions.
 */
final class ScalaFmtSettingsPanel private[settings](
  settings: CodeStyleSettings,
  configResolver: ScalafmtSettingsConfigResolver
) extends ScalaCodeStylePanelBase(settings, "Scalafmt") {

  def this(settings: CodeStyleSettings) = this(settings, ScalafmtSettingsConfigResolver)

  import ScalaFmtSettingsPanel._

  override val getEditor: EditorEx = createConfigEditor

  override def dispose(): Unit = {
    cancelResolution()
    super.dispose()
    if (getEditor != null) {
      EditorFactory.getInstance().releaseEditor(getEditor)
    }
  }

  override def apply(settings: CodeStyleSettings): Unit = {
    val editorText = getEditor.getDocument.getText
    val configTextChangedInEditor = previewState.source.exists(_.originalText != editorText)

    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val formatterChanged = isPanelEnabled != scalaSettings.USE_SCALAFMT_FORMATTER

    val modified = isModified(settings) || formatterChanged
    if (!modified) return

    scalaSettings.SCALAFMT_SHOW_INVALID_CODE_WARNINGS = showScalaFmtInvalidCodeWarnings.isSelected
    scalaSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT = useIntellijFormatterForRangeFormat.isSelected
    scalaSettings.SCALAFMT_REFORMAT_ON_FILES_SAVE = reformatOnFileSaveCheckBox.isSelected
    scalaSettings.SCALAFMT_FALLBACK_TO_DEFAULT_SETTINGS = fallBackToDefaultSettings.isSelected

    val configPathNew = configPathFromUi

    // Apply persists the requested path immediately; missing-file feedback arrives with the background lookup.
    scalaSettings.SCALAFMT_CONFIG_PATH = configPathNew
    if (configTextChangedInEditor && previewState.requestedPath.contains(configPathNew)) {
      previewState.source.foreach { config =>
        inWriteAction(config.document.setText(editorText))
      }
    }

    isPanelEnabled = scalaSettings.USE_SCALAFMT_FORMATTER
    if (!isPanelEnabled) {
      cancelResolution()
      updateConfigurationUi()
    } else if (formatterChanged || configTextChangedInEditor || !previewState.requestedPath.contains(configPathNew)) {
      reloadConfiguration()
    }
    updateUseIntellijWarningVisibility(scalaSettings)
  }

  private def configPathFromUi: String =
    externalFormatterSettingsPath.getText.trim

  //noinspection HardCodedStringLiteral
  private def updateScalafmtVersionLabel(version: String, isDefault: Boolean = false): Unit = {
    if (scalafmtVersionLabel == null) return
    scalafmtVersionLabel.setText(version + (if (isDefault) s" (${ScalaSettingsBundle.message("scalafmt.default")})" else ""))
  }

  @RequiresEdt
  private def updateResolution(result: Result): Unit = result match {
    case Right(Version(version, isDefault)) =>
      updateScalafmtVersionLabel(version.toString, isDefault)
    case Left(failure) =>
      handleResolutionFailure(failure)
  }

  @RequiresEdt
  private def handleResolutionFailure(failure: Failure): Unit = failure match {
    case Failure(error: ConfigError, Some(Version(version, isDefault))) =>
      updateScalafmtVersionLabel(version.toString, isDefault)
      reportConfigResolveError(error)
    case Failure(error: ConfigError, None) =>
      reportConfigResolveError(error)
    case Failure(ConfigScalafmtResolveError(error), _) =>
      updateScalafmtVersionLabel("")
      reportCantResolveVersion(error.version)
  }

  private def reportConfigResolveError(configResolveError: ConfigResolveError): Unit = {
    import ConfigResolveError._
    configResolveError match {
      case ConfigFileNotFound(configPath) => reportConfigFileNotFound(configPath)
      case ConfigParseError(_, exception) => reportConfigParseError(exception.getMessage)
      case ConfigCyclicDependenciesError(_, _) => reportConfigParseError(ScalaFormattingBundle.message("scalafmt.config.load.errors.cyclic.includes.detected"))
      case ConfigScalafmtResolveError(error) => reportCantResolveVersion(error.version)
    }
  }

  private def reportConfigParseError(errorMessage: String): Unit = {
    val component = previewPanel
    displayMessage(
      ScalaSettingsBundle.message("scalafmt.settings.panel.errors.failed.to.parse.config", errorMessage),
      component, component.getWidth - 10, component.getHeight - 55, Balloon.Position.above,
      MessageType.WARNING
    )
  }

  private def reportCantResolveVersion(version: ScalafmtVersion): Unit = {
    val component = previewPanel
    displayMessage(
      ScalaSettingsBundle.message("scalafmt.settings.panel.errors.cant.resolve.version", version),
      component, component.getWidth - 10, component.getHeight - 55, Balloon.Position.above,
      MessageType.WARNING
    )
  }

  private def reportConfigFileNotFound(configPath: String): Unit = {
    val component = externalFormatterSettingsPath
    displayMessage(
      ScalaSettingsBundle.message("scalafmt.settings.panel.errors.cant.find.config.file", configPath),
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

  @RequiresEdt
  private def cancelResolution(): Unit = {
    resolutionSession.cancel()
    previewState match {
      case PreviewState.Loading(_) => previewState = PreviewState.Unloaded
      case _ =>
    }
  }

  @RequiresEdt
  private def reloadConfiguration(createConfig: Boolean = false): Unit = {
    cancelResolution()

    val path = configPathFromUi
    val initialPreview = if (projectOpt.isDefined) PreviewState.Loading(path) else PreviewState.Unloaded
    updatePreview(initialPreview)
    updateScalafmtVersionLabel("")

    val modalityState = ModalityState.current()
    projectOpt.foreach(startConfigurationResolution(path, createConfig, modalityState))
  }

  @RequiresEdt
  private def startConfigurationResolution(
    path: String,
    createConfig: Boolean,
    modalityState: ModalityState
  )(project: Project): Unit =
    resolutionSession.start(startConfigurationTask(project, path, createConfig, modalityState))

  @RequiresEdt
  private def startConfigurationTask(
    project: Project,
    path: String,
    createConfig: Boolean,
    modalityState: ModalityState
  )(request: Request): ProgressIndicator =
    configResolver.resolveConfigAsync(
      project,
      path,
      onLoaded = onConfigurationLoaded(project, path, modalityState, request),
      onFinished = onConfigurationFinished(project, modalityState, request),
      createConfig
    )

  private def onConfigurationLoaded(
    project: Project,
    path: String,
    modalityState: ModalityState,
    request: Request
  )(config: Option[LoadedConfig]): Unit =
    request.invokeLaterIfCurrent(modalityState, project) {
      val state = config.fold[PreviewState](PreviewState.Missing(path))(PreviewState.Loaded(path, _))
      updatePreview(state)
    }

  private def onConfigurationFinished(
    project: Project,
    modalityState: ModalityState,
    request: Request
  )(completion: Completion): Unit =
    request.invokeLaterIfCurrent(modalityState, project) {
      resolutionSession.finish(request)
      completeResolution(completion)
    }

  @RequiresEdt
  private def completeResolution(completion: Completion): Unit = completion match {
    case Completion.Resolved(result) =>
      updateResolution(result)
    case Completion.Cancelled =>
      previewState match {
        case PreviewState.Loading(path) =>
          updatePreview(PreviewState.Cancelled(path))
        case _ =>
          // Retain a loaded preview, including edits made while resolving the formatter.
      }
    case Completion.Failed(_) =>
      previewState match {
        case PreviewState.Loading(path) =>
          updatePreview(PreviewState.Failed(path))
        case _ =>
          displayMessage(
            ScalaSettingsBundle.message("scalafmt.settings.panel.configuration.failed"),
            externalFormatterSettingsPath, 0, externalFormatterSettingsPath.getHeight,
            Balloon.Position.below, MessageType.WARNING
          )
      }
  }

  @RequiresEdt
  private def updatePreview(state: PreviewState): Unit = {
    previewState = state
    setPreviewText(state.source.fold("")(_.originalText))
    updateConfigurationUi()
  }

  @RequiresEdt
  private def setPreviewText(text: String): Unit =
    WriteIntentReadAction.run(() => {
      inWriteAction(getEditor.getDocument.setText(text))
    })

  override def isModified(settings: CodeStyleSettings): Boolean = {
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaSettings.SCALAFMT_CONFIG_PATH.trim != configPathFromUi ||
      scalaSettings.SCALAFMT_SHOW_INVALID_CODE_WARNINGS != showScalaFmtInvalidCodeWarnings.isSelected ||
      scalaSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT != useIntellijFormatterForRangeFormat.isSelected ||
      scalaSettings.SCALAFMT_REFORMAT_ON_FILES_SAVE != reformatOnFileSaveCheckBox.isSelected ||
      scalaSettings.SCALAFMT_FALLBACK_TO_DEFAULT_SETTINGS != fallBackToDefaultSettings.isSelected ||
      previewState.source.exists(_.originalText != getEditor.getDocument.getText)
  }

  override def resetImpl(codeStyleSettings: CodeStyleSettings): Unit = {
    val scalaSettings = codeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val configPath = scalaSettings.SCALAFMT_CONFIG_PATH.trim

    externalFormatterSettingsPath.setText(configPath)
    showScalaFmtInvalidCodeWarnings.setSelected(scalaSettings.SCALAFMT_SHOW_INVALID_CODE_WARNINGS)
    useIntellijFormatterForRangeFormat.setSelected(scalaSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT)
    reformatOnFileSaveCheckBox.setSelected(scalaSettings.SCALAFMT_REFORMAT_ON_FILES_SAVE)
    fallBackToDefaultSettings.setSelected(scalaSettings.SCALAFMT_FALLBACK_TO_DEFAULT_SETTINGS)

    isPanelEnabled = scalaSettings.USE_SCALAFMT_FORMATTER
    if (isPanelEnabled) {
      reloadConfiguration()
    } else {
      cancelResolution()
      updateConfigurationUi()
    }

    updateUseIntellijWarningVisibility(scalaSettings)
  }

  override protected def getPanelInner: JComponent = {
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

    val inner = new JPanel(new GridLayoutManager(7, 3, JBUI.insets(10, 15), -1, -1))

    showScalaFmtInvalidCodeWarnings = new JBCheckBox(ScalaSettingsBundle.message("scalafmt.settings.panel.show.warnings.when.formatting.invalid.code"))
    useIntellijFormatterForRangeFormat = new JBCheckBox(ScalaSettingsBundle.message("scalafmt.settings.panel.use.intellij.formatter.for.code.range.formatting"))
    useIntellijWarning = new JLabel(AllIcons.General.Warning)
    useIntellijWarning.setToolTipText(ScalaSettingsBundle.message("scalafmt.settings.panel.use.intellij.formatter.for.code.range.formatting.warning.tooltip"))
    useIntellijFormatterForRangeFormat.addChangeListener((_: ChangeEvent) => {
      useIntellijWarning.setVisible(!useIntellijFormatterForRangeFormat.isSelected)
    })
    val useIntellijFormatterWrapper = {
      val w = new JPanel(new GridLayoutManager(1, 2, JBUI.emptyInsets(), -1, -1))
      w.add(useIntellijFormatterForRangeFormat, constraint(0, 0, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
      w.add(useIntellijWarning, constraint(0, 1, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
      w
    }

    def saveActionsShortcutsText: String = {
      val actionManager = ActionManager.getInstance

      val saveAllShortcut = Option(actionManager.getKeyboardShortcut("SaveAll"))
      val saveDocumentShortcut = Option(actionManager.getKeyboardShortcut("SaveDocument"))

      val shortcuts = saveAllShortcut.toSeq ++ saveDocumentShortcut
      val shortcutTexts = shortcuts.map(KeymapUtil.getShortcutText)
      shortcutTexts.mkString(", ")
    }

    reformatOnFileSaveCheckBox = new JBCheckBox(ScalaSettingsBundle.message("scalafmt.settings.panel.reformat.on.file.save", saveActionsShortcutsText))
    reformatOnFileSaveComment = ComponentPanelBuilder.createCommentComponent(ScalaSettingsBundle.message("scalafmt.settings.panel.reformat.on.file.save.tooltip", KeymapUtil.getShortcutText("ReformatCode")), false): @nowarn("cat=deprecation")
    fallBackToDefaultSettings = new JBCheckBox(ScalaSettingsBundle.message("scalafmt.settings.panel.fallback.to.default.settings"))

    inner.add(showScalaFmtInvalidCodeWarnings,
      constraint(0, 0, 1, 3, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
    inner.add(useIntellijFormatterWrapper,
      constraint(1, 0, 1, 3, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
    inner.add(reformatOnFileSaveCheckBox,
      constraint(2, 0, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
    inner.add(reformatOnFileSaveComment,
      constraint(2, 1, 1, 2, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
    inner.add(fallBackToDefaultSettings,
      constraint(3, 0, 1, 3, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))

    val configPathTextField = new JBTextField
    //noinspection HardCodedStringLiteral
    configPathTextField.getEmptyText.setText(ScalaSettingsBundle.message("scalafmt.default.path", DefaultConfigFilePath))
    externalFormatterSettingsPath = new TextFieldWithBrowseButton(configPathTextField)
    installConfigPathListeners()

    //noinspection HardCodedStringLiteral
    inner.add(new JLabel(ScalaSettingsBundle.message("scalafmt.settings.panel.configuration.label") + ":"),
      constraint(4, 0, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
    inner.add(externalFormatterSettingsPath,
      constraint(4, 1, 1, 1, ANCHOR_NORTHWEST, FILL_HORIZONTAL, SIZEPOLICY_CAN_GROW | SIZEPOLICY_WANT_GROW, SIZEPOLICY_FIXED))
    inner.add(new Spacer,
      constraint(4, 2, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_WANT_GROW, SIZEPOLICY_CAN_SHRINK))

    scalafmtVersionLabel = new JLabel()
    val scalafmtVersionCaption = new JLabel(ScalaSettingsBundle.message("scalafmt.settings.panel.scalafmt.version.label") + ":")
    scalafmtVersionCaption.setLabelFor(scalafmtVersionLabel)
    inner.add(scalafmtVersionCaption,
      constraint(5, 0, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
    inner.add(scalafmtVersionLabel,
      constraint(5, 1, 1, 1, ANCHOR_NORTHWEST, FILL_HORIZONTAL, SIZEPOLICY_CAN_GROW | SIZEPOLICY_WANT_GROW, SIZEPOLICY_FIXED))
    inner.add(new Spacer,
      constraint(5, 2, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_WANT_GROW, SIZEPOLICY_CAN_SHRINK))

    val configEditorPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 10, true, true))

    configStatusGroup = new JPanel(new HorizontalLayout(10, SwingConstants.CENTER))
    configStatusLabel = new JLabel()
    configStatusGroup.add(configStatusLabel)
    configStatusButton = new JButton(ScalaFormattingBundle.message("scalafmt.can.not.find.config.file.create.new"))
    configStatusButton.addActionListener { _ =>
      reloadConfiguration(createConfig = previewState.isInstanceOf[PreviewState.Missing])
    }
    configStatusGroup.add(configStatusButton)

    configEditorPanel.add(configStatusGroup)

    previewPanel = new JPanel()
    configEditorPanel.add(previewPanel)
    installPreviewPanel(previewPanel)
    getEditor.getComponent.setPreferredSize(configEditorPanel.getPreferredSize)
    inner.add(configEditorPanel,
      constraint(6, 0, 1, 3, ANCHOR_NORTH, FILL_BOTH, SIZEPOLICY_CAN_GROW, SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW))

    updateConfigurationUi()

    inner
  }

  private def installConfigPathListeners(): Unit = {
    val textAccessor = new TextComponentAccessor[JTextField]() {
      override def setText(textField: JTextField, text: String): Unit = {
        textField.setText(text)
        onConfigPathCommitted()
      }

      override def getText(textField: JTextField): String = textField.getText
    }

    externalFormatterSettingsPath.getTextField.addFocusListener(new FocusAdapter {
      override def focusLost(e: FocusEvent): Unit = onConfigPathCommitted()
    })

    val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("conf")
      .withTitle(customSettingsTitle)
      .withDescription(customSettingsTitle)
    externalFormatterSettingsPath.addActionListener { event =>
      browseConfiguration(event, descriptor, textAccessor)
    }
  }

  @RequiresEdt
  private def browseConfiguration(
    event: ActionEvent,
    descriptor: FileChooserDescriptor,
    textAccessor: TextComponentAccessor[JTextField]
  ): Unit = {
    val project = projectOpt
    val path = configPathFromUi

    // The platform chooser accepts only absolute initial paths. Resolve relative paths and
    // the VCS-root fallback off EDT, independently of whether a preview has arrived yet.
    val lookup = ReadAction.nonBlocking[Option[VirtualFile]](() => initialBrowseFile(project, path))
      .expireWith(this)
      .expireWhen(() => project.exists(_.isDisposed))
      .coalesceBy(this, externalFormatterSettingsPath)
      .finishOnUiThread(ModalityState.current(), { initialFile =>
        if (projectOpt == project && configPathFromUi == path) {
          // Create the chooser with the current binding; listeners are installed only once.
          val browseAction = new ComponentWithBrowseButton.BrowseFolderActionListener[JTextField](
            externalFormatterSettingsPath, project.orNull, descriptor, textAccessor
          ) {
            override protected def getInitialFile: VirtualFile = initialFile.orNull
          }

          browseAction.actionPerformed(event)
        }
      })

    lookup.submit(AppExecutorUtil.getAppExecutorService)
  }

  @RequiresBackgroundThread
  private def initialBrowseFile(project: Option[Project], configPath: String): Option[VirtualFile] = {
    // An empty path selects the default configuration file in the project's file chooser.
    val actualPath = ScalafmtConfigUtils.actualConfigPath(configPath)
    val absolutePath = project.flatMap(ScalafmtConfigUtils.projectConfigFileAbsolutePath(_, actualPath)).getOrElse(actualPath)
    var candidate = NioFiles.toPath(absolutePath)
    if (candidate == null || !candidate.isAbsolute) return None

    // Preserve BrowseFolderRunnable's nearest-existing-parent fallback without doing its VFS lookup on EDT.
    while (candidate != null) {
      val file = VirtualFileManager.getInstance().findFileByNioPath(candidate)
      if (file != null) return Some(file)
      candidate = candidate.getParent
    }
    None
  }

  private def onConfigPathCommitted(): Unit = {
    val path = configPathFromUi
    if (path.isEmpty) externalFormatterSettingsPath.setText(null)
    // Compare requested paths without probing the file system, including while a lookup is still pending.
    // Reset and Create reload explicitly; ordinary focus changes preserve unapplied preview edits.
    if (isPanelEnabled && !previewState.requestedPath.contains(path)) reloadConfiguration()
  }

  @RequiresEdt
  def onProjectSet(aProject: Project): Unit = {
    projectOpt = Some(aProject)
    resetImpl(settings)
  }

  private def updateUseIntellijWarningVisibility(settings: ScalaCodeStyleSettings): Unit = {
    if (useIntellijWarning == null) return
    useIntellijWarning.setVisible(!settings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT)
  }

  private def updateConfigurationUi(): Unit = {
    val statusText = previewState match {
      case PreviewState.Missing(_) => Some(ScalaSettingsBundle.message("scalafmt.settings.panel.no.config.found.under.path.using.default"))
      case PreviewState.Cancelled(_) => Some(ScalaSettingsBundle.message("scalafmt.settings.panel.configuration.cancelled"))
      case PreviewState.Failed(_) => Some(ScalaSettingsBundle.message("scalafmt.settings.panel.configuration.failed"))
      case _ => None
    }
    configStatusLabel.setText(statusText.orNull)
    val actionText = previewState match {
      case PreviewState.Cancelled(_) | PreviewState.Failed(_) => ScalaSettingsBundle.message("scalafmt.settings.panel.retry")
      case _ => ScalaFormattingBundle.message("scalafmt.can.not.find.config.file.create.new")
    }
    configStatusButton.setText(actionText)
    previewPanel.setVisible(isPanelEnabled && previewState.source.isDefined)
    configStatusGroup.setVisible(isPanelEnabled && statusText.isDefined)
  }

  private var projectOpt: Option[Project] = None
  private var isPanelEnabled: Boolean = false

  private var previewState: PreviewState = PreviewState.Unloaded

  /**
   * Owns asynchronous configuration work and guards preview, version and error updates for this panel.
   *
   * For example, configuration A starts downloading formatter `2.7.5`.
   * The user then selects configuration B with cached formatter `3.11.5`,
   * whose result updates the version label first. When A finishes later, applying
   * its result would incorrectly display `2.7.5` while B is selected.
   * The session's request guard skips A's stale UI update even if cooperative cancellation has not stopped its worker.
   *
   * Each lookup starts a new request guarding both preview and resolution updates.
   * Replacement, reset while disabled, and disposal invalidate outstanding completions
   * and cancel the previous worker. Checking on EDT after dispatch also prevents
   * already queued results from updating a newer selection.
   */
  private val resolutionSession = new ConfigurationResolutionSession

  private var scalafmtVersionLabel: JLabel = scala.compiletime.uninitialized
  private var configStatusGroup: JComponent = scala.compiletime.uninitialized
  private var configStatusLabel: JLabel = scala.compiletime.uninitialized
  private var configStatusButton: JButton = scala.compiletime.uninitialized
  private var previewPanel: JPanel = scala.compiletime.uninitialized
  private var myPanel: JPanel = scala.compiletime.uninitialized
  private var externalFormatterSettingsPath: TextFieldWithBrowseButton = scala.compiletime.uninitialized
  private var showScalaFmtInvalidCodeWarnings: JBCheckBox = scala.compiletime.uninitialized
  private var useIntellijFormatterForRangeFormat: JBCheckBox = scala.compiletime.uninitialized
  private var useIntellijWarning: JLabel = scala.compiletime.uninitialized
  private var reformatOnFileSaveCheckBox: JBCheckBox = scala.compiletime.uninitialized
  private var reformatOnFileSaveComment: JComponent = scala.compiletime.uninitialized
  private var fallBackToDefaultSettings: JBCheckBox = scala.compiletime.uninitialized

  private val customSettingsTitle = ScalaSettingsBundle.message("scalafmt.settings.panel.select.custom.scalafmt.configuration.file")
  //noinspection HardCodedStringLiteral
  private val DefaultConfigFilePath = s".${java.io.File.separatorChar}${ScalafmtDynamicConfigService.DefaultConfigurationFileName}"
}

object ScalaFmtSettingsPanel {

  /**
   * Availability of the configuration preview, independent of formatter download progress.
   * A loaded preview keeps its requested path and source together so Apply cannot mix files.
   */
  private sealed abstract class PreviewState(val requestedPath: Option[String]) {
    def source: Option[LoadedConfig] = None
  }

  private object PreviewState {
    /**
     * No preview or requested path is retained, either initially or after invalidating
     * a lookup that had not loaded a configuration yet.
     */
    case object Unloaded extends PreviewState(None)

    /**
     * A lookup is pending for this path, so configuration availability is still unknown.
     * Both the preview and the Create action remain hidden until loading completes.
     */
    final case class Loading(path: String) extends PreviewState(Some(path))

    /**
     * No configuration was loaded for this path, so the panel offers to create one.
     * Formatter resolution can still continue using the default version.
     */
    final case class Missing(path: String) extends PreviewState(Some(path))

    /**
     * Loading was cancelled before a preview arrived; the panel offers Retry.
     * Cancellation after preview delivery retains the loaded configuration instead.
     */
    final case class Cancelled(path: String) extends PreviewState(Some(path))

    /**
     * An unexpected failure prevented preview delivery; the panel offers Retry.
     * Failures after loading retain the preview, including any unapplied edits.
     */
    final case class Failed(path: String) extends PreviewState(Some(path))

    /**
     * The source document and its initial text are available for an editable preview,
     * independently of whether formatter resolution or configuration validation succeeds.
     * Keeping the requested path with the source lets Apply target the correct document.
     */
    final case class Loaded(path: String, config: LoadedConfig) extends PreviewState(Some(path)) {
      override def source: Option[LoadedConfig] = Some(config)
    }
  }

  object SearchFilter {
    def ScalafmtConfiguration: String = ScalafmtDynamicConfigServiceImpl.ScalafmtConfigurationSearchFilter
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

  private def fillEditorSettings(settings: EditorSettings,
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
