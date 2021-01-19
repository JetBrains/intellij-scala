package org.jetbrains.idea.devkit.scala.project

import com.intellij.ide.util.projectWizard.{SdkSettingsStep, SettingsStep}
import com.intellij.openapi.projectRoots.{JavaSdk, SdkTypeId}
import com.intellij.openapi.ui.{ComboBox, Messages}
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.UI
import org.jetbrains.idea.devkit.scala.DevkitBundle
import org.jetbrains.idea.devkit.scala.project.SbtIdeaPluginProjectBuilder.NewProjectSettings
import org.jetbrains.plugins.scala.extensions.ObjectExt

//noinspection ReferencePassedToNls
class SbtIdeaPluginWizardStep(settingsStep: SettingsStep, moduleBuilder: SbtIdeaPluginProjectBuilder, defaultSettings: NewProjectSettings) extends
  SdkSettingsStep(settingsStep, moduleBuilder, (_: SdkTypeId).is[JavaSdk]) {

  private val pluginNameField = new JBTextField()
  private val vendorNameField = new JBTextField()
  private val intelliJBuildField = new JBTextField(defaultSettings.intelliJBuildNumber)
  private val platformKindSelector = new ComboBox[SbtIdeaPluginPlatformKind](SbtIdeaPluginPlatformKind.values())

  {
    pluginNameField.getEmptyText.setText(defaultSettings.pluginName)
    vendorNameField.getEmptyText.setText(defaultSettings.pluginVendor)
    val pluginNamePanel = UI.PanelFactory.panel(pluginNameField).withTooltip(DevkitBundle.message("sbtidea.template.plugin.name.help")).createPanel()
    val vendorNamePanel = UI.PanelFactory.panel(vendorNameField).withTooltip(DevkitBundle.message("sbtidea.template.plugin.vendor.help")).createPanel()
    val intelliJBuildPanel = UI.PanelFactory.panel(intelliJBuildField).withTooltip(DevkitBundle.message("sbtidea.template.intellij.build.help")).createPanel()
    settingsStep.addSettingsField(DevkitBundle.message("sbtidea.template.plugin.name"), pluginNamePanel)
    settingsStep.addSettingsField(DevkitBundle.message("sbtidea.template.plugin.vendor"), vendorNamePanel)
    settingsStep.addSettingsField(DevkitBundle.message("sbtidea.template.intellij.build"), intelliJBuildPanel)
    settingsStep.addSettingsField(DevkitBundle.message("sbtidea.template.platform.kind"), platformKindSelector)
  }

  override def updateDataModel(): Unit = {
    updateSettings()
    super.updateDataModel()
  }

  private def updateSettings(): Unit = {
    moduleBuilder.updateNewProjectSettings(
      NewProjectSettings(
        pluginNameField.getText,
        vendorNameField.getText,
        intelliJBuildField.getText,
        platformKindSelector.getItem
      )
    )
  }

  override def validate(): Boolean = {
    updateSettings()
    if (pluginNameField.getText.toLowerCase.contains("plugin")) {
      Messages.showWarningDialog(DevkitBundle.message("invalid.plugin.name"), DevkitBundle.message("invalid.plugin.name.title"))
      return false
    }
    if (pluginNameField.getText.isEmpty || vendorNameField.getText.isEmpty || intelliJBuildField.getText.isEmpty) {
      Messages.showWarningDialog(DevkitBundle.message("incomplete.plugin.info"), DevkitBundle.message("incomplete.plugin.info.title"))
      return false
    }
    super.validate()
  }
}