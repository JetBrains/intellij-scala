package org.jetbrains.sbt
package project.settings

import java.awt.{Component, FlowLayout}

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil._
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Condition
import javax.swing._
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

    val button = new JButton(SbtBundle.message("sbt.settings.new"))

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

  private val resolveClassifiersCheckBox = new JCheckBox(SbtBundle.message("sbt.settings.resolveClassifiers"))
  private val resolveSbtClassifiersCheckBox = new JCheckBox(SbtBundle.message("sbt.settings.resolveSbtClassifiers"))
  private val useSbtShellForImportCheckBox = new JCheckBox(SbtBundle.message("sbt.settings.useShellForImport"))
  private val useSbtShellForBuildCheckBox = new JCheckBox(SbtBundle.message("sbt.settings.useShellForBuild"))
  private val remoteDebugSbtShell = new JCheckBox(SbtBundle.message("sbt.settings.remoteDebug"))
  private val allowSbtVersionOverride = new JCheckBox(SbtBundle.message("sbt.settings.allowSbtVersionOverride"))

  override def fillExtraControls(@NotNull content: PaintAwarePanel, indentLevel: Int): Unit = {
    val labelConstraints = getLabelConstraints(indentLevel)
    val fillLineConstraints = getFillLineConstraints(indentLevel)

    val downloadPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
    downloadPanel.add(resolveClassifiersCheckBox)
    downloadPanel.add(resolveSbtClassifiersCheckBox)
    content.add(new JLabel(SbtBundle.message("sbt.settings.download")), labelConstraints)
    content.add(downloadPanel, fillLineConstraints)

    val sbtShellPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
    val useSbtShellLabel = new JLabel(SbtBundle.message("sbt.settings.useShell"))
    sbtShellPanel.add(useSbtShellLabel)
    sbtShellPanel.add(useSbtShellForImportCheckBox)
    sbtShellPanel.add(useSbtShellForBuildCheckBox)
    content.add(sbtShellPanel, fillLineConstraints)

    val optionPanel = new JPanel()
    optionPanel.setLayout(new BoxLayout(optionPanel, BoxLayout.Y_AXIS))
    optionPanel.add(allowSbtVersionOverride)
    optionPanel.add(remoteDebugSbtShell)
    remoteDebugSbtShell.setAlignmentX(Component.LEFT_ALIGNMENT)
    content.add(optionPanel, fillLineConstraints)

    if (context == Context.Wizard) {
      val label = new JLabel(SbtBundle.message("sbt.settings.project.jdk"))
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

  override def isExtraSettingModified: Boolean = {
    val settings = getInitialSettings

    selectedJdkName != settings.jdkName ||
      resolveClassifiersCheckBox.isSelected != settings.resolveClassifiers ||
      resolveSbtClassifiersCheckBox.isSelected != settings.resolveSbtClassifiers ||
      useSbtShellForImportCheckBox.isSelected != settings.useSbtShellForImport ||
      useSbtShellForBuildCheckBox.isSelected != settings.useSbtShellForBuild ||
      remoteDebugSbtShell.isSelected != settings.enableDebugSbtShell ||
      allowSbtVersionOverride.isSelected != settings.allowSbtVersionOverride
  }

  override protected def resetExtraSettings(isDefaultModuleCreation: Boolean): Unit = {
    val settings = getInitialSettings

    val jdk = settings.jdkName.flatMap(name => Option(ProjectJdkTable.getInstance.findJdk(name)))
    jdkComboBox.setSelectedJdk(jdk.orNull)

    resolveClassifiersCheckBox.setSelected(settings.resolveClassifiers)
    resolveSbtClassifiersCheckBox.setSelected(settings.resolveSbtClassifiers)

    useSbtShellForImportCheckBox.setSelected(settings.importWithShell)
    useSbtShellForBuildCheckBox.setSelected(settings.buildWithShell)

    remoteDebugSbtShell.setSelected(settings.enableDebugSbtShell)
    allowSbtVersionOverride.setSelected(settings.allowSbtVersionOverride)
  }

  override def updateInitialExtraSettings(): Unit = {
    applyExtraSettings(getInitialSettings)
  }

  override protected def applyExtraSettings(settings: SbtProjectSettings): Unit = {
    settings.jdk = selectedJdkName.orNull
    settings.resolveClassifiers = resolveClassifiersCheckBox.isSelected
    settings.resolveSbtClassifiers = resolveSbtClassifiersCheckBox.isSelected
    settings.useSbtShellForImport = useSbtShellForImportCheckBox.isSelected
    settings.enableDebugSbtShell = remoteDebugSbtShell.isSelected
    settings.allowSbtVersionOverride = allowSbtVersionOverride.isSelected

    val useSbtShellForBuildSettingChanged =
      settings.useSbtShellForBuild != useSbtShellForBuildCheckBox.isSelected

    if (useSbtShellForBuildSettingChanged) {
      import org.jetbrains.plugins.scala.findUsages.compilerReferences.ScalaCompilerReferenceService
      import org.jetbrains.plugins.scala.findUsages.compilerReferences.compilation.CompilerMode
      settings.useSbtShellForBuild = useSbtShellForBuildCheckBox.isSelected
      val project = getProject

      // locking here is hardly ideal, but we assume that transactions are very short
      // and hope for the best
      if (project != null) {
        ScalaCompilerReferenceService(project).inTransaction { case (_, publisher) =>
          val newMode =
            if (settings.useSbtShellForBuild) CompilerMode.SBT
            else                              CompilerMode.JPS

          publisher.onCompilerModeChange(newMode)
        }
      }
    }
  }

  private def selectedJdkName = Option(jdkComboBox.getSelectedJdk).map(_.getName)

  override def validate(sbtProjectSettings: SbtProjectSettings): Boolean = selectedJdkName.isDefined
}

