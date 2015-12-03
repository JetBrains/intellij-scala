package org.jetbrains.sbt
package project.settings

import java.awt.FlowLayout
import javax.swing._

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil._
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Condition
import org.jetbrains.annotations.NotNull

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

    val addToTable = new Condition[Sdk] {
      override def value(sdk: Sdk): Boolean = {
        inWriteAction {
          val table = ProjectJdkTable.getInstance()
          if (!table.getAllJdks.contains(sdk)) table.addJdk(sdk)
        }
        true
      }
    }

    result.setSetupButton(button, null, model, new JdkComboBox.NoneJdkComboBoxItem, addToTable, false)

    result
  }

  private val resolveClassifiersCheckBox = new JCheckBox(SbtBundle("sbt.settings.resolveClassifiers"))
  private val resolveJavadocsCheckBox = new JCheckBox(SbtBundle("sbt.settings.resolveJavadocs"))
  private val resolveSbtClassifiersCheckBox = new JCheckBox(SbtBundle("sbt.settings.resolveSbtClassifiers"))

  def fillExtraControls(@NotNull content: PaintAwarePanel, indentLevel: Int) {
    val downloadPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
    downloadPanel.add(resolveClassifiersCheckBox)
    downloadPanel.add(resolveJavadocsCheckBox)
    downloadPanel.add(resolveSbtClassifiersCheckBox)
    content.add(new JLabel("Download:"), getLabelConstraints(indentLevel))
    content.add(downloadPanel, getFillLineConstraints(indentLevel))

    if (context == Context.Wizard) {
      val label = new JLabel("Project \u001BSDK:")
      label.setLabelFor(jdkComboBox)

      val jdkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
      jdkPanel.add(jdkComboBox)
      jdkPanel.add(jdkComboBox.getSetUpButton)

      content.add(label, getLabelConstraints(indentLevel))
      content.add(jdkPanel, getFillLineConstraints(indentLevel))
    }
  }

  def isExtraSettingModified = {
    val settings = getInitialSettings

    selectedJdkName != settings.jdkName ||
      resolveClassifiersCheckBox.isSelected != settings.resolveClassifiers ||
      resolveSbtClassifiersCheckBox.isSelected != settings.resolveClassifiers ||
      resolveJavadocsCheckBox.isSelected != settings.resolveJavadocs
  }

  protected def resetExtraSettings(isDefaultModuleCreation: Boolean) {
    val settings = getInitialSettings

    val jdk = settings.jdkName.flatMap(name => Option(ProjectJdkTable.getInstance.findJdk(name)))
    jdkComboBox.setSelectedJdk(jdk.orNull)

    resolveClassifiersCheckBox.setSelected(settings.resolveClassifiers)
    resolveSbtClassifiersCheckBox.setSelected(settings.resolveSbtClassifiers)
    resolveJavadocsCheckBox.setSelected(settings.resolveJavadocs)
  }

  override def updateInitialExtraSettings() {
    applyExtraSettings(getInitialSettings)
  }

  protected def applyExtraSettings(settings: SbtProjectSettings) {
    settings.jdk = selectedJdkName.orNull
    settings.resolveClassifiers = resolveClassifiersCheckBox.isSelected
    settings.resolveSbtClassifiers = resolveSbtClassifiersCheckBox.isSelected
    settings.resolveJavadocs = resolveJavadocsCheckBox.isSelected
  }

  private def selectedJdkName = Option(jdkComboBox.getSelectedJdk).map(_.getName)

  def validate(sbtProjectSettings: SbtProjectSettings) = selectedJdkName.isDefined
}
