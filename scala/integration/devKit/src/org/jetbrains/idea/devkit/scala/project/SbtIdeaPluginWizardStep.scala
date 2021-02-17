package org.jetbrains.idea.devkit.scala.project

import com.intellij.ide.util.projectWizard.{SdkSettingsStep, SettingsStep}
import com.intellij.openapi.projectRoots.{JavaSdk, SdkTypeId}
import com.intellij.openapi.ui.{ComboBox, Messages}
import com.intellij.ui.components.{JBCheckBox, JBTextField}
import com.intellij.util.ui.UI
import org.jetbrains.idea.devkit.scala.DevkitBundle
import org.jetbrains.idea.devkit.scala.project.SbtIdeaPluginProjectBuilder.NewProjectSettings
import org.jetbrains.plugins.scala.extensions.ObjectExt

import javax.swing.JTextField

//noinspection ReferencePassedToNls
class SbtIdeaPluginWizardStep(settingsStep: SettingsStep, moduleBuilder: SbtIdeaPluginProjectBuilder, defaultSettings: NewProjectSettings, projectNameField: JTextField) extends
  SdkSettingsStep(settingsStep, moduleBuilder, (_: SdkTypeId).is[JavaSdk]) {

  private val includeSamplesCB = new JBCheckBox(DevkitBundle.message("sbtidea.template.samples.tooltip"))
  private val vendorNameField = new JBTextField()
  private val intelliJBuildField = new JBTextField(defaultSettings.intelliJBuildNumber)
  private val platformKindSelector = new ComboBox[SbtIdeaPluginPlatformKind](SbtIdeaPluginPlatformKind.values())

  {
    vendorNameField.getEmptyText.setText(defaultSettings.pluginVendor)
    includeSamplesCB.setSelected(defaultSettings.includeSamples)
    settingsStep.addSettingsField(DevkitBundle.message("sbtidea.template.plugin.vendor"), vendorNameField)
    settingsStep.addSettingsField(DevkitBundle.message("sbtidea.template.intellij.build"), intelliJBuildField)
    settingsStep.addSettingsField(DevkitBundle.message("sbtidea.template.platform.kind"), platformKindSelector)
    settingsStep.addSettingsComponent(includeSamplesCB)
  }

  override def updateDataModel(): Unit = {
    updateSettings()
    super.updateDataModel()
  }

  private def updateSettings(): Unit = {
    moduleBuilder.updateNewProjectSettings(
      NewProjectSettings(
        includeSamplesCB.isSelected,
        projectNameField.getText,
        vendorNameField.getText,
        intelliJBuildField.getText,
        platformKindSelector.getItem
      )
    )
  }

  override def validate(): Boolean = {
    updateSettings()
    if (projectNameField.getText.toLowerCase.contains("plugin")) {
      Messages.showWarningDialog(DevkitBundle.message("invalid.plugin.name"), DevkitBundle.message("invalid.plugin.name.title"))
      return false
    }
    if (projectNameField.getText.isEmpty || vendorNameField.getText.isEmpty || intelliJBuildField.getText.isEmpty) {
      Messages.showWarningDialog(DevkitBundle.message("incomplete.plugin.info"), DevkitBundle.message("incomplete.plugin.info.title"))
      return false
    }
    super.validate()
  }
}