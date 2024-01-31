package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil._
import com.intellij.openapi.externalSystem.util.{ExternalSystemUtil, PaintAwarePanel}
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, SdkTypeId}
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Condition
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.messages.Topic
import com.intellij.util.ui.{GridBag, JBUI}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.project.external.SdkUtils
import org.jetbrains.sbt.project.SbtProjectSystem

import java.awt.{FlowLayout, GridBagConstraints}
import javax.swing._

/**
 * The settings UI is used it two places with slightly different UI:
 *  1. In `Settings | Build, Execution, Deployment | Build Tools | sbt` in `sbt Projects` subsection
 *  1. During new project creation via `Import project from existing sources` (`File | New | Project from Existing Sources...`)
 */
class SbtProjectSettingsControl(context: Context, initialSettings: SbtProjectSettings)
  extends AbstractExternalProjectSettingsControl[SbtProjectSettings](initialSettings) {

  private val model = new ProjectSdksModel()

  private val jdkComboBox: JdkComboBox = {
    model.reset(getProject)
    val jdkFilter: Condition[SdkTypeId] = (sdk: SdkTypeId) => sdk == JavaSdk.getInstance()

    new JdkComboBox(getProject, model, jdkFilter, null, jdkFilter, SdkUtils.addJdkIfNotExists)
  }

  private val extraControls = new SbtExtraControls()

  override def fillExtraControls(@NotNull content: PaintAwarePanel, indentLevel: Int): Unit = {
    val labelConstraints = getLabelConstraints(indentLevel)
    val fillLineConstraints = getFillLineConstraints(indentLevel)

    val rootComponent = extraControls.rootComponent

    if (context == Context.Wizard) {
      content.add(rootComponent, fillLineConstraints)
      val label = new JLabel(SbtBundle.message("sbt.settings.project.jdk"))
      label.setLabelFor(jdkComboBox)

      val jdkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
      jdkPanel.add(jdkComboBox)

      content.add(label, labelConstraints)
      content.add(jdkPanel, fillLineConstraints)

      extraControls.remoteDebugSbtShellCheckBox.panelWithTooltip.setVisible(false)
    } else {
      // This scroll pane was introduced, because when we consider the scenario that these settings will be used
      // in "Settings | Build, Execution, Deployment | Build Tools | sbt" and the user minimizes the window as much as possible,
      // the checkbox at the bottom ("Enable debugging") is not fully visible.
      val scrollPane = new JBScrollPane(rootComponent)
      scrollPane.setBorder(null)
      content.add(scrollPane, fillLineAndColumnConstraints(indentLevel))
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
      extraControls.scalaVersionPreferenceCheckBox.isSelected != settings.preferScala2 ||
      extraControls.groupProjectsFromSameBuildCheckBox.isSelected != settings.groupProjectsFromSameBuild ||
      extraControls.insertProjectTransitiveDependencies.isSelected != settings.insertProjectTransitiveDependencies ||
      extraControls.useSeparateCompilerOutputPaths.isSelected != settings.useSeparateCompilerOutputPaths
  }

  override protected def resetExtraSettings(isDefaultModuleCreation: Boolean): Unit = {
    val settings = getInitialSettings

    // note: this should be changed to model.syncSdks when https://youtrack.jetbrains.com/issue/IDEA-343316 is fixed
    model.reset(null)
    // note: it is done to keep jdkComboBox in sync with global SDKs list
    jdkComboBox.reloadModel()
    val jdk = settings.jdkName.flatMap(name => Option(ProjectJdkTable.getInstance.findJdk(name)))
    jdkComboBox.setSelectedJdk(jdk.orNull)

    extraControls.converterVersion = settings.converterVersion
    extraControls.resolveClassifiersCheckBox.setSelected(settings.resolveClassifiers)
    extraControls.resolveSbtClassifiersCheckBox.setSelected(settings.resolveSbtClassifiers)
    extraControls.useSbtShellForImportCheckBox.setSelected(settings.importWithShell)
    extraControls.useSbtShellForBuildCheckBox.setSelected(settings.buildWithShell)
    extraControls.remoteDebugSbtShellCheckBox.setSelected(settings.enableDebugSbtShell)
    extraControls.scalaVersionPreferenceCheckBox.setSelected(settings.preferScala2)
    extraControls.groupProjectsFromSameBuildCheckBox.setSelected(settings.groupProjectsFromSameBuild)
    extraControls.insertProjectTransitiveDependencies.setSelected(settings.insertProjectTransitiveDependencies)
    extraControls.useSeparateCompilerOutputPaths.setSelected(settings.useSeparateCompilerOutputPaths)
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
    settings.preferScala2 = extraControls.scalaVersionPreferenceCheckBox.isSelected
    settings.groupProjectsFromSameBuild = extraControls.groupProjectsFromSameBuildCheckBox.isSelected
    settings.insertProjectTransitiveDependencies = extraControls.insertProjectTransitiveDependencies.isSelected

    val shouldReloadProject =
      settings.useSeparateCompilerOutputPaths != extraControls.useSeparateCompilerOutputPaths.isSelected
    settings.useSeparateCompilerOutputPaths = extraControls.useSeparateCompilerOutputPaths.isSelected

    val useSbtShellForBuildSettingChanged =
      settings.useSbtShellForBuild != extraControls.useSbtShellForBuildCheckBox.isSelected

    val project = getProject

    if (useSbtShellForBuildSettingChanged) {
      val newSetting = extraControls.useSbtShellForBuildCheckBox.isSelected
      settings.useSbtShellForBuild = newSetting

      if (project != null) {
        val newMode = if (newSetting) CompilerMode.SBT else CompilerMode.JPS
        project.getMessageBus.syncPublisher(SbtProjectSettingsControl.CompilerModeChangeTopic).onCompilerModeChange(newMode)
      }
    }

    if (shouldReloadProject) {
      val builder = new ImportSpecBuilder(project, SbtProjectSystem.Id).use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
      ExternalSystemUtil.refreshProjects(builder)
    }
  }

  private def selectedJdkName = Option(jdkComboBox.getSelectedJdk).map(_.getName)

  private def fillLineAndColumnConstraints(indentLevel: Int): GridBag = {
    val insets = JBUI.insets(INSETS, INSETS + INSETS * indentLevel, 0, INSETS)
    new GridBag().weightx(1).coverLine().coverColumn().fillCell().anchor(GridBagConstraints.WEST).insets(insets)
  }

  override def validate(sbtProjectSettings: SbtProjectSettings): Boolean = selectedJdkName.isDefined
}

private[jetbrains] object SbtProjectSettingsControl {
  private[jetbrains] trait CompilerModeChangeListener {
    def onCompilerModeChange(mode: CompilerMode): Unit
  }

  private[jetbrains] val CompilerModeChangeTopic: Topic[CompilerModeChangeListener] =
    new Topic("Compiler references search compiler mode change topic", classOf[CompilerModeChangeListener])
}
