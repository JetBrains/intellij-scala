package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import org.jetbrains.annotations.NotNull
import javax.swing._
import java.awt.FlowLayout
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil._
import com.intellij.openapi.projectRoots.ProjectJdkTable

/**
 * @author Pavel Fatin
 */
class SbtProjectSettingsControl(context: Context, initialSettings: SbtProjectSettings)
        extends AbstractExternalProjectSettingsControl[SbtProjectSettings](initialSettings) {
  
  private val jdkComboBox: JdkComboBox = {
    val model = new ProjectSdksModel()
    model.reset(null)

    val result = new JdkComboBox(model)

    val button = new JButton("Ne\u001Bw...")
    result.setSetupButton(button, null, model, new JdkComboBox.NoneJdkComboBoxItem, null, false)

    result
  }

  private val resolveClassifiersCheckBox = new JCheckBox("Download sources and docs")

  private val resolveSbtClassifiersCheckBox = new JCheckBox("Download SBT sources and docs")

  def fillExtraControls(@NotNull content: PaintAwarePanel, indentLevel: Int) {
    val label = new JLabel("Project \u001BSDK:")
    label.setLabelFor(jdkComboBox)

    val jdkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT))
    jdkPanel.add(label)
    jdkPanel.add(jdkComboBox)
    jdkPanel.add(jdkComboBox.getSetUpButton)

    content.add(resolveClassifiersCheckBox, getFillLineConstraints(indentLevel))
    content.add(resolveSbtClassifiersCheckBox, getFillLineConstraints(indentLevel))

    if (context == Context.Wizard) {
      content.add(jdkPanel, getFillLineConstraints(indentLevel))
    }
  }

  def isExtraSettingModified = {
    val settings = getInitialSettings

    selectedJdkName != settings.jdkName ||
            resolveClassifiersCheckBox.isSelected != settings.resolveClassifiers ||
            resolveSbtClassifiersCheckBox.isSelected != settings.resolveClassifiers
  }

  protected def resetExtraSettings(isDefaultModuleCreation: Boolean) {
    val settings = getInitialSettings
    
    val jdk = settings.jdkName.flatMap(name => Option(ProjectJdkTable.getInstance.findJdk(name)))
    jdkComboBox.setSelectedJdk(jdk.orNull)

    resolveClassifiersCheckBox.setSelected(settings.resolveClassifiers)
    resolveSbtClassifiersCheckBox.setSelected(settings.resolveSbtClassifiers)
  }

  override def updateInitialExtraSettings() {
    applyExtraSettings(getInitialSettings)
  }

  protected def applyExtraSettings(settings: SbtProjectSettings) {
    settings.jdk = selectedJdkName.orNull
    settings.resolveClassifiers = resolveClassifiersCheckBox.isSelected
    settings.resolveSbtClassifiers = resolveSbtClassifiersCheckBox.isSelected
  }

  private def selectedJdkName = Option(jdkComboBox.getSelectedJdk).map(_.getName)

  def validate(sbtProjectSettings: SbtProjectSettings) = selectedJdkName.isDefined
}