package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil._
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, SdkTypeId}
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Condition
import org.jetbrains.annotations.NotNull

import java.awt.FlowLayout
import javax.swing._

class SbtProjectSettingsControl(context: Context, initialSettings: SbtProjectSettings)
  extends AbstractExternalProjectSettingsControl[SbtProjectSettings](initialSettings) {

  private val jdkComboBox: JdkComboBox = {
    val model = new ProjectSdksModel()
    model.reset(getProject)
    val jdkFilter: Condition[SdkTypeId] = (sdk: SdkTypeId) => sdk == JavaSdk.getInstance()

    new JdkComboBox(getProject, model, jdkFilter, null, jdkFilter, null)
  }

  private val extraControls = new SbtExtraControls()

  override def fillExtraControls(@NotNull content: PaintAwarePanel, indentLevel: Int): Unit = {
    val labelConstraints = getLabelConstraints(indentLevel)
    val fillLineConstraints = getFillLineConstraints(indentLevel)

    content.add(extraControls.rootComponent, fillLineConstraints)

    if (context == Context.Wizard) {
      val label = new JLabel(SbtBundle.message("sbt.settings.project.jdk"))
      label.setLabelFor(jdkComboBox)

      val jdkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
      jdkPanel.add(jdkComboBox)

      content.add(label, labelConstraints)
      content.add(jdkPanel, fillLineConstraints)

      extraControls.remoteDebugSbtShellCheckBox.panelWithTooltip.setVisible(false)
    }
  }

  override def isExtraSettingModified: Boolean = {
    val settings = getInitialSettings

    selectedJdkName != settings.jdkName ||
      extraControls.resolveClassifiersCheckBox.isSelected != settings.resolveClassifiers ||
      extraControls.resolveSbtClassifiersCheckBox.isSelected != settings.resolveSbtClassifiers ||
      extraControls.useSbtShellForImportCheckBox.isSelected != settings.useSbtShellForImport ||
      extraControls.useSbtShellForBuildCheckBox.isSelected != settings.useSbtShellForBuild ||
      extraControls.remoteDebugSbtShellCheckBox.isSelected != settings.enableDebugSbtShell ||
      extraControls.allowSbtVersionOverrideCheckBox.isSelected != settings.allowSbtVersionOverride ||
      extraControls.scalaVersionPreferenceComboBox.getSelectedIndex == 0 != settings.preferScala2
  }

  override protected def resetExtraSettings(isDefaultModuleCreation: Boolean): Unit = {
    val settings = getInitialSettings

    val jdk = settings.jdkName.flatMap(name => Option(ProjectJdkTable.getInstance.findJdk(name)))
    jdkComboBox.setSelectedJdk(jdk.orNull)

    extraControls.converterVersion = settings.converterVersion
    extraControls.resolveClassifiersCheckBox.setSelected(settings.resolveClassifiers)
    extraControls.resolveSbtClassifiersCheckBox.setSelected(settings.resolveSbtClassifiers)
    extraControls.useSbtShellForImportCheckBox.setSelected(settings.importWithShell)
    extraControls.useSbtShellForBuildCheckBox.setSelected(settings.buildWithShell)
    extraControls.remoteDebugSbtShellCheckBox.setSelected(settings.enableDebugSbtShell)
    extraControls.allowSbtVersionOverrideCheckBox.setSelected(settings.allowSbtVersionOverride)
    extraControls.scalaVersionPreferenceComboBox.setSelectedIndex(if (settings.preferScala2) 0 else 1)
  }

  override def updateInitialExtraSettings(): Unit = {
    applyExtraSettings(getInitialSettings)
  }

  override protected def applyExtraSettings(settings: SbtProjectSettings): Unit = {
    settings.converterVersion = extraControls.converterVersion
    settings.jdk = selectedJdkName.orNull
    settings.resolveClassifiers = extraControls.resolveClassifiersCheckBox.isSelected
    settings.resolveSbtClassifiers = extraControls.resolveSbtClassifiersCheckBox.isSelected
    settings.useSbtShellForImport = extraControls.useSbtShellForImportCheckBox.isSelected
    settings.enableDebugSbtShell = extraControls.remoteDebugSbtShellCheckBox.isSelected
    settings.allowSbtVersionOverride = extraControls.allowSbtVersionOverrideCheckBox.isSelected
    settings.preferScala2 = extraControls.scalaVersionPreferenceComboBox.getSelectedIndex == 0

    val useSbtShellForBuildSettingChanged =
      settings.useSbtShellForBuild != extraControls.useSbtShellForBuildCheckBox.isSelected

    if (useSbtShellForBuildSettingChanged) {
      import org.jetbrains.plugins.scala.findUsages.compilerReferences.ScalaCompilerReferenceService
      import org.jetbrains.plugins.scala.findUsages.compilerReferences.compilation.CompilerMode
      settings.useSbtShellForBuild = extraControls.useSbtShellForBuildCheckBox.isSelected
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

