package org.jetbrains.plugins.scala.lang.formatting.settings

import java.awt._

import com.intellij.application.options._
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager}
import javax.swing._
import javax.swing.event.ChangeEvent
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.rearranger.ScalaArrangementPanel

/**
 * User: Alefas
 * Date: 23.09.11
 */
class ScalaTabbedCodeStylePanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings)
  extends TabbedLanguageCodeStylePanel(ScalaLanguage.INSTANCE, currentSettings, settings) {

  protected override def initTabs(settings: CodeStyleSettings) {
    super.initTabs(settings)
    addTab(new ScalaDocFormattingPanel(settings))
    addTab(new ImportsPanel(settings))
    addTab(new MultiLineStringCodeStylePanel(settings))
    addTab(new TypeAnnotationsPanel(settings))
    addTab(new ScalaArrangementPanel(settings))
    addTab(new OtherCodeStylePanel(settings))
    initOuterFormatterPanel()
  }

  override def isModified(settings: CodeStyleSettings): Boolean = {
    val scalaCodeStyleSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaCodeStyleSettings.USE_SCALAFMT_FORMATTER != useExternalFormatterCheckbox.isSelected ||
      scalaCodeStyleSettings.USE_CUSTOM_SCALAFMT_CONFIG_PATH != overrideExternalFormatterSettings.isSelected ||
      scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH != externalFormatterSettingsPath.getText || super.isModified(settings)
  }

  override def apply(settings: CodeStyleSettings): Unit = {
    val scalaCodeStyleSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaCodeStyleSettings.USE_SCALAFMT_FORMATTER = useExternalFormatterCheckbox.isSelected
    scalaCodeStyleSettings.USE_CUSTOM_SCALAFMT_CONFIG_PATH = overrideExternalFormatterSettings.isSelected
    scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH = externalFormatterSettingsPath.getText
    super.apply(settings)
  }

  override def resetImpl(settings: CodeStyleSettings): Unit = {
    val scalaCodeStyleSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    useExternalFormatterCheckbox.setSelected(scalaCodeStyleSettings.USE_SCALAFMT_FORMATTER)
    overrideExternalFormatterSettings.setSelected(scalaCodeStyleSettings.USE_CUSTOM_SCALAFMT_CONFIG_PATH)
    externalFormatterSettingsPath.setText(scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH)
    super.resetImpl(settings)
  }

  private def initOuterFormatterPanel(): Unit = {
    outerPanel = new JPanel(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1))
    externalFormatterPanel = new JPanel(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1))
    useExternalFormatterCheckbox = new JCheckBox("Use external formatter")
    externalFormatterPanel.add(useExternalFormatterCheckbox,
      new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
        null, 0, false))
    overrideExternalFormatterSettings = new JCheckBox("Override settings")
    externalFormatterPanel.add(overrideExternalFormatterSettings,
      new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
      null, 0, false))
    externalFormatterSettingsPath = new TextFieldWithBrowseButton()
    externalFormatterSettingsPath.addBrowseFolderListener(customSettingsTitile, null, null,
      new FileChooserDescriptor(true, false, false, false, false, false))
    externalFormatterPanel.add(externalFormatterSettingsPath,
      new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
        null, 0, false))
    outerPanel.add(externalFormatterPanel,
      new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_FIXED,
        GridConstraints.SIZEPOLICY_FIXED, null, null,
        null, 0, false))
    val dummyPanel = new JPanel(new BorderLayout)
    dummyPanel.add(innerPanel)
    outerPanel.add(dummyPanel,
      new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
        null, 0, false))
    useExternalFormatterCheckbox.addChangeListener((e: ChangeEvent) => {
      settings.getCustomSettings(classOf[ScalaCodeStyleSettings]).USE_SCALAFMT_FORMATTER = useExternalFormatterCheckbox.isSelected
      innerPanel.setVisible(!useExternalFormatterCheckbox.isSelected)
      overrideExternalFormatterSettings.setEnabled(useExternalFormatterCheckbox.isSelected)
    })
    overrideExternalFormatterSettings.addChangeListener((e: ChangeEvent) => {
      externalFormatterSettingsPath.setEnabled(overrideExternalFormatterSettings.isSelected)
    })
  }

  private var useExternalFormatterCheckbox: JCheckBox = _
  private var overrideExternalFormatterSettings: JCheckBox = _
  private var externalFormatterSettingsPath: TextFieldWithBrowseButton = _
  private var externalFormatterPanel: JPanel = _
  private var outerPanel: JPanel = _
  private def innerPanel = super.getPanel
  private val customSettingsTitile = "Select custom scalafmt configuration file"

  override def getPanel: JComponent = outerPanel
}