package org.jetbrains.sbt
package project.settings

import java.awt.{Component, FlowLayout}
import javax.swing._
import javax.swing.event.{ChangeEvent, ChangeListener}

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil._
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Condition
import com.intellij.ui.components.{JBLabel, JBTextField}
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager}
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
  private val remoteDebugSbtShell = new JCheckBox(SbtBundle("sbt.settings.remoteDebug"))
  private val remoteDebugPortLabel = new JBLabel(SbtBundle("sbt.settings.remoteDebugPort"))
  private val remoteDebugPortText = new JBTextField(6)
  private val remoteDebugBalloonBuilder =
    JBPopupFactory.getInstance().createBalloonBuilder(new JBTextField("Invalid port selected.")).setTitle("Warning!")

  def fillExtraControls(@NotNull content: PaintAwarePanel, indentLevel: Int) {
    val labelConstraints = getLabelConstraints(indentLevel)
    val fillLineConstraints = getFillLineConstraints(indentLevel)

    val downloadPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
    downloadPanel.add(resolveClassifiersCheckBox)
    downloadPanel.add(resolveSbtClassifiersCheckBox)
    content.add(new JLabel("Download:"), labelConstraints)
    content.add(downloadPanel, fillLineConstraints)

    val optionPanel = new JPanel()
    optionPanel.setLayout(new BoxLayout(optionPanel, BoxLayout.Y_AXIS))
    optionPanel.add(useSbtShellCheckBox)
    remoteDebugSbtShell.addChangeListener(new ChangeListener(){
      override def stateChanged(e: ChangeEvent): Unit = remoteDebugPortText.setEnabled(remoteDebugSbtShell.isSelected)
    })
    optionPanel.add(remoteDebugSbtShell)
    remoteDebugSbtShell.setAlignmentX(Component.LEFT_ALIGNMENT)
    val remoteDebugPortPanel = new JPanel(new GridLayoutManager(2, 2))
    remoteDebugPortPanel.setAlignmentX(Component.LEFT_ALIGNMENT)
    remoteDebugPortText.setMaximumSize(remoteDebugPortText.getPreferredSize)
    remoteDebugPortPanel.add(remoteDebugPortLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false))
    remoteDebugPortPanel.add(remoteDebugPortText, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false))
    optionPanel.add(remoteDebugPortPanel)
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

      content.getComponents.toSeq.foreachDefined {
        case checkbox: JCheckBox
          if checkbox.getText.startsWith("Use auto-import") =>
          checkbox.setSelected(false)
          Option(checkbox.getParent).foreach(_.remove(checkbox))
      }
    }
  }

  def isExtraSettingModified: Boolean = {
    val settings = getInitialSettings

    selectedJdkName != settings.jdkName ||
      resolveClassifiersCheckBox.isSelected != settings.resolveClassifiers ||
      resolveSbtClassifiersCheckBox.isSelected != settings.resolveSbtClassifiers ||
      useSbtShellCheckBox.isSelected != settings.useSbtShell ||
      remoteDebugSbtShell.isSelected != settings.useRemoteDebugSbtShell ||
      remoteDebugPortText.getText != settings.remoteDebugPort
  }

  protected def resetExtraSettings(isDefaultModuleCreation: Boolean) {
    val settings = getInitialSettings

    val jdk = settings.jdkName.flatMap(name => Option(ProjectJdkTable.getInstance.findJdk(name)))
    jdkComboBox.setSelectedJdk(jdk.orNull)

    resolveClassifiersCheckBox.setSelected(settings.resolveClassifiers)
    resolveSbtClassifiersCheckBox.setSelected(settings.resolveSbtClassifiers)
    useSbtShellCheckBox.setSelected(settings.useSbtShell)
    remoteDebugSbtShell.setSelected(settings.useRemoteDebugSbtShell)
    remoteDebugPortText.setText(settings.remoteDebugPort)
  }

  override def updateInitialExtraSettings() {
    applyExtraSettings(getInitialSettings)
  }

  protected def applyExtraSettings(settings: SbtProjectSettings) {
    settings.jdk = selectedJdkName.orNull
    settings.resolveClassifiers = resolveClassifiersCheckBox.isSelected
    settings.resolveSbtClassifiers = resolveSbtClassifiersCheckBox.isSelected
    settings.useSbtShell = useSbtShellCheckBox.isSelected
    settings.useRemoteDebugSbtShell = remoteDebugSbtShell.isSelected
    settings.remoteDebugPort = remoteDebugPortText.getText
    checkPort(settings)
  }

  protected def checkPort(settings: SbtProjectSettings): Unit = {
    def popupWarning(): Unit = {
      remoteDebugBalloonBuilder.createBalloon().showInCenterOf(remoteDebugPortText)
    }
    if (settings.remoteDebugPort != null && settings.remoteDebugPort.nonEmpty) try {
      val port = Integer.parseInt(settings.remoteDebugPort)
      if (port < 0 || port >= 65536) popupWarning()
    } catch {
      case _: NumberFormatException => popupWarning()
    }
  }

  private def selectedJdkName = Option(jdkComboBox.getSelectedJdk).map(_.getName)

  def validate(sbtProjectSettings: SbtProjectSettings): Boolean = selectedJdkName.isDefined
}
