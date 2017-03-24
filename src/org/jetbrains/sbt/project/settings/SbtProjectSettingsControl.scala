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
import org.jetbrains.plugins.scala.extensions._

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
  private val useSbtShellCheckBox = new JCheckBox(SbtBundle("sbt.settings.useShell"))

  def fillExtraControls(@NotNull content: PaintAwarePanel, indentLevel: Int) {
    val labelConstraints = getLabelConstraints(indentLevel)
    val fillLineConstraints = getFillLineConstraints(indentLevel)

    val downloadPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
    downloadPanel.add(resolveClassifiersCheckBox)
    downloadPanel.add(resolveSbtClassifiersCheckBox)
    content.add(new JLabel("Download:"), labelConstraints)
    content.add(downloadPanel, fillLineConstraints)

    val optionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
    optionPanel.add(useSbtShellCheckBox)
    content.add(optionPanel, fillLineConstraints)

    // TODO Remove the patching when the External System will provide this functionality natively
    content.getComponents.toSeq.foreachDefined {
      case checkbox: JCheckBox
        if checkbox.getText.startsWith("Create directories") =>
        // set it to off so that it doesn't stay enabled for people who clicked it before it was removed
        checkbox.setSelected(false)
        Option(checkbox.getParent).foreach(_.remove(checkbox))
    }

    if (context == Context.Wizard) {
      val label = new JLabel("Project \u001BJDK:")
      label.setLabelFor(jdkComboBox)

      val jdkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
      jdkPanel.add(jdkComboBox)
      jdkPanel.add(jdkComboBox.getSetUpButton)

      content.add(label, labelConstraints)
      content.add(jdkPanel, fillLineConstraints)

      // hide the sbt shell option until it matures (SCL-10984)
      useSbtShellCheckBox.setVisible(false)
    }
  }

  def isExtraSettingModified: Boolean = {
    val settings = getInitialSettings

    selectedJdkName != settings.jdkName ||
      resolveClassifiersCheckBox.isSelected != settings.resolveClassifiers ||
      resolveSbtClassifiersCheckBox.isSelected != settings.resolveSbtClassifiers ||
      useSbtShellCheckBox.isSelected != settings.useSbtShell
  }

  protected def resetExtraSettings(isDefaultModuleCreation: Boolean) {
    val settings = getInitialSettings

    val jdk = settings.jdkName.flatMap(name => Option(ProjectJdkTable.getInstance.findJdk(name)))
    jdkComboBox.setSelectedJdk(jdk.orNull)

    resolveClassifiersCheckBox.setSelected(settings.resolveClassifiers)
    resolveSbtClassifiersCheckBox.setSelected(settings.resolveSbtClassifiers)
    useSbtShellCheckBox.setSelected(settings.useSbtShell)
  }

  override def updateInitialExtraSettings() {
    applyExtraSettings(getInitialSettings)
  }

  protected def applyExtraSettings(settings: SbtProjectSettings) {
    settings.jdk = selectedJdkName.orNull
    settings.resolveClassifiers = resolveClassifiersCheckBox.isSelected
    settings.resolveSbtClassifiers = resolveSbtClassifiersCheckBox.isSelected
    settings.useSbtShell = useSbtShellCheckBox.isSelected
  }

  private def selectedJdkName = Option(jdkComboBox.getSelectedJdk).map(_.getName)

  def validate(sbtProjectSettings: SbtProjectSettings): Boolean = selectedJdkName.isDefined
}
