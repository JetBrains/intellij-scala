package org.jetbrains.plugins.scala.lang.formatting.settings

import java.awt._
import java.io.File

import com.intellij.application.options._
import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.components.JBTextField
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
      scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH != externalFormatterSettingsPath.getText || super.isModified(settings)
  }

  override def apply(settings: CodeStyleSettings): Unit = {
    val scalaCodeStyleSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaCodeStyleSettings.USE_SCALAFMT_FORMATTER = useExternalFormatterCheckbox.isSelected
    scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH = externalFormatterSettingsPath.getText
    super.apply(settings)
  }

  override def resetImpl(settings: CodeStyleSettings): Unit = {
    val scalaCodeStyleSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    useExternalFormatterCheckbox.setSelected(scalaCodeStyleSettings.USE_SCALAFMT_FORMATTER)
    externalFormatterSettingsPath.setEnabled(scalaCodeStyleSettings.USE_SCALAFMT_FORMATTER)
    externalFormatterSettingsPath.setText(scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH)
    super.resetImpl(settings)
  }

  private def initOuterFormatterPanel(): Unit = {
    outerPanel = new JPanel(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1))
    externalFormatterPanel = new JPanel(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1))
    useExternalFormatterCheckbox = new JCheckBox("Use scalafmt")
    externalFormatterPanel.add(useExternalFormatterCheckbox,
      new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
        null, 0, false))
    externalFormatterPanel.add(new JLabel("Scalafmt config file path:"),
      new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
      null, 0, false))
    val myTextField = new JBTextField
    myTextField.getEmptyText.setText(s"Default: .${File.separatorChar}scalafmt.conf")
    externalFormatterSettingsPath = new TextFieldWithBrowseButton(myTextField)
    externalFormatterSettingsPath.addBrowseFolderListener(customSettingsTitle, customSettingsTitle, null,
      FileChooserDescriptorFactory.createSingleFileDescriptor("conf"))
    externalFormatterPanel.add(externalFormatterSettingsPath,
      new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW,
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
    useExternalFormatterCheckbox.addChangeListener((_: ChangeEvent) => {
      //USE_SCALAFMT_FORMATTER setting is immediately set to allow proper formatting for core formatter examples
      settings.getCustomSettings(classOf[ScalaCodeStyleSettings]).USE_SCALAFMT_FORMATTER = useExternalFormatterCheckbox.isSelected
      innerPanel.setVisible(!useExternalFormatterCheckbox.isSelected)
      externalFormatterSettingsPath.setEnabled(useExternalFormatterCheckbox.isSelected)
    })
  }

  private var useExternalFormatterCheckbox: JCheckBox = _
  private var externalFormatterSettingsPath: TextFieldWithBrowseButton = _
  private var externalFormatterPanel: JPanel = _
  private var outerPanel: JPanel = _
  private def innerPanel = super.getPanel
  private val customSettingsTitle = "Select custom scalafmt configuration file"
  private var myModel: CodeStyleSchemesModel = _

  override def getPanel: JComponent = outerPanel
}