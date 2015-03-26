package org.jetbrains.sbt
package project.settings

import java.awt.{Component, FlowLayout}
import javax.swing._

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil._
import com.intellij.openapi.externalSystem.util.{ExternalSystemUiUtil, ExternalSystemBundle, PaintAwarePanel}
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Condition
import com.intellij.ui.components.JBCheckBox
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

  private val resolveSbtClassifiersCheckBox = new JCheckBox(SbtBundle("sbt.settings.resolveSbtClassifiers"))

  private val useOurOwnAutoImportCheckBox = new JCheckBox(ExternalSystemBundle.message("settings.label.use.auto.import"))

  private var oldAutoImportBox: Option[Component] = None

  def fillExtraControls(@NotNull content: PaintAwarePanel, indentLevel: Int) {
    oldAutoImportBox = Some(content.getComponent(content.getComponentCount-2))
    content.add(useOurOwnAutoImportCheckBox,
      ExternalSystemUiUtil.getFillLineConstraints(indentLevel),
      content.getComponentCount-2)

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
      useOurOwnAutoImportCheckBox.isSelected != settings.useOurOwnAutoImport ||
      resolveClassifiersCheckBox.isSelected != settings.resolveClassifiers ||
      resolveSbtClassifiersCheckBox.isSelected != settings.resolveClassifiers
  }

  protected def resetExtraSettings(isDefaultModuleCreation: Boolean) {
    val settings = getInitialSettings

    oldAutoImportBox.foreach(_.setVisible(false))
    useOurOwnAutoImportCheckBox.setSelected(settings.useOurOwnAutoImport)

    val jdk = settings.jdkName.flatMap(name => Option(ProjectJdkTable.getInstance.findJdk(name)))
    jdkComboBox.setSelectedJdk(jdk.orNull)

    resolveClassifiersCheckBox.setSelected(settings.resolveClassifiers)
    resolveSbtClassifiersCheckBox.setSelected(settings.resolveSbtClassifiers)
  }

  override def updateInitialExtraSettings() {
    applyExtraSettings(getInitialSettings)
  }

  protected def applyExtraSettings(settings: SbtProjectSettings) {
    settings.setUseAutoImport(false)
    settings.useOurOwnAutoImport = useOurOwnAutoImportCheckBox.isSelected
    settings.jdk = selectedJdkName.orNull
    settings.resolveClassifiers = resolveClassifiersCheckBox.isSelected
    settings.resolveSbtClassifiers = resolveSbtClassifiersCheckBox.isSelected
  }

  private def selectedJdkName = Option(jdkComboBox.getSelectedJdk).map(_.getName)

  def validate(sbtProjectSettings: SbtProjectSettings) = selectedJdkName.isDefined
}
