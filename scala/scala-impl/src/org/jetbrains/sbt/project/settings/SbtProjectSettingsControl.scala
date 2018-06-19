package org.jetbrains.sbt
package project.settings

import java.awt.{Component, FlowLayout}
import javax.swing._

import com.intellij.openapi.externalSystem.service.settings.{AbstractExternalProjectSettingsControl, ExternalSystemSettingsControlCustomizer}
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil._
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Condition
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.sbt.project.settings.SbtProjectSettingsControl._

/**
 * @author Pavel Fatin
 */
class SbtProjectSettingsControl(context: Context, initialSettings: SbtProjectSettings)
        extends AbstractExternalProjectSettingsControl[SbtProjectSettings](null, initialSettings, {
          if (context == Context.Wizard) customizerInWizard else customizer
        }) {

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
        false
      }
    }

    result.setSetupButton(button, null, model, new JdkComboBox.NoneJdkComboBoxItem, addToTable, false)

    result
  }

  private val resolveClassifiersCheckBox = new JCheckBox(SbtBundle("sbt.settings.resolveClassifiers"))
  private val resolveSbtClassifiersCheckBox = new JCheckBox(SbtBundle("sbt.settings.resolveSbtClassifiers"))
  private val useSbtShellForImportCheckBox = new JCheckBox(SbtBundle("sbt.settings.useShellForImport"))
  private val useSbtShellForBuildCheckBox = new JCheckBox(SbtBundle("sbt.settings.useShellForBuild"))
  private val remoteDebugSbtShell = new JCheckBox(SbtBundle("sbt.settings.remoteDebug"))

  override def fillExtraControls(@NotNull content: PaintAwarePanel, indentLevel: Int) {
    val labelConstraints = getLabelConstraints(indentLevel)
    val fillLineConstraints = getFillLineConstraints(indentLevel)

    val downloadPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
    downloadPanel.add(resolveClassifiersCheckBox)
    downloadPanel.add(resolveSbtClassifiersCheckBox)
    content.add(new JLabel("Download:"), labelConstraints)
    content.add(downloadPanel, fillLineConstraints)

    val sbtShellPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
    val useSbtShellLabel = new JLabel(SbtBundle("sbt.settings.useShell"))
    sbtShellPanel.add(useSbtShellLabel)
    sbtShellPanel.add(useSbtShellForImportCheckBox)
    sbtShellPanel.add(useSbtShellForBuildCheckBox)
    content.add(sbtShellPanel, fillLineConstraints)

    val optionPanel = new JPanel()
    optionPanel.setLayout(new BoxLayout(optionPanel, BoxLayout.Y_AXIS))
    optionPanel.add(remoteDebugSbtShell)
    remoteDebugSbtShell.setAlignmentX(Component.LEFT_ALIGNMENT)
    content.add(optionPanel, fillLineConstraints)

    if (context == Context.Wizard) {
      val label = new JLabel("Project \u001BJDK:")
      label.setLabelFor(jdkComboBox)

      val jdkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
      jdkPanel.add(jdkComboBox)
      jdkPanel.add(jdkComboBox.getSetUpButton)

      content.add(label, labelConstraints)
      content.add(jdkPanel, fillLineConstraints)

      // hide the sbt shell option until it matures (SCL-10984)
      // useSbtShellCheckBox.setVisible(false)
      remoteDebugSbtShell.setVisible(false)
    }
  }

  def isExtraSettingModified: Boolean = {
    val settings = getInitialSettings

    selectedJdkName != settings.jdkName ||
      resolveClassifiersCheckBox.isSelected != settings.resolveClassifiers ||
      resolveSbtClassifiersCheckBox.isSelected != settings.resolveSbtClassifiers ||
      useSbtShellForImportCheckBox.isSelected != settings.useSbtShellForImport ||
      useSbtShellForBuildCheckBox.isSelected != settings.useSbtShellForBuild ||
      remoteDebugSbtShell.isSelected != settings.enableDebugSbtShell
  }

  protected def resetExtraSettings(isDefaultModuleCreation: Boolean) {
    val settings = getInitialSettings

    val jdk = settings.jdkName.flatMap(name => Option(ProjectJdkTable.getInstance.findJdk(name)))
    jdkComboBox.setSelectedJdk(jdk.orNull)

    resolveClassifiersCheckBox.setSelected(settings.resolveClassifiers)
    resolveSbtClassifiersCheckBox.setSelected(settings.resolveSbtClassifiers)

    // option migration
    val useShellForImport = settings.useSbtShellForImport || settings.useSbtShell
    val useShellForBuild = settings.useSbtShellForBuild || settings.useSbtShell
    useSbtShellForImportCheckBox.setSelected(useShellForImport)
    useSbtShellForBuildCheckBox.setSelected(useShellForBuild)
    remoteDebugSbtShell.setSelected(settings.enableDebugSbtShell)
  }

  override def updateInitialExtraSettings() {
    applyExtraSettings(getInitialSettings)
  }

  protected def applyExtraSettings(settings: SbtProjectSettings) {
    settings.jdk = selectedJdkName.orNull
    settings.resolveClassifiers = resolveClassifiersCheckBox.isSelected
    settings.resolveSbtClassifiers = resolveSbtClassifiersCheckBox.isSelected
    settings.useSbtShellForBuild = useSbtShellForBuildCheckBox.isSelected
    settings.useSbtShellForImport = useSbtShellForImportCheckBox.isSelected
    settings.enableDebugSbtShell = remoteDebugSbtShell.isSelected
    settings.useSbtShell = false
  }

  private def selectedJdkName = Option(jdkComboBox.getSelectedJdk).map(_.getName)

  def validate(sbtProjectSettings: SbtProjectSettings): Boolean = selectedJdkName.isDefined
}

object SbtProjectSettingsControl {

  def customizer = new ExternalSystemSettingsControlCustomizer(false, true, true)
  def customizerInWizard = new ExternalSystemSettingsControlCustomizer(true, true, true)

}