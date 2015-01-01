package org.jetbrains.plugins.hocon.codestyle

import com.intellij.application.options.{CodeStyleAbstractConfigurable, TabbedLanguageCodeStylePanel}
import com.intellij.psi.codeStyle.{CodeStyleSettings, CodeStyleSettingsProvider, DisplayPriority}
import org.jetbrains.plugins.hocon.lang.HoconLanguage

class HoconCodeStyleSettingsProvider extends CodeStyleSettingsProvider {

  override def getConfigurableDisplayName = "HOCON"

  override def getPriority = DisplayPriority.COMMON_SETTINGS

  def createSettingsPage(settings: CodeStyleSettings, originalSettings: CodeStyleSettings) =
    new CodeStyleAbstractConfigurable(settings, originalSettings, "HOCON") {
      override protected def createPanel(settings: CodeStyleSettings) =
        new TabbedLanguageCodeStylePanel(HoconLanguage, getCurrentSettings, settings) {}

      def getHelpTopic = null

    }

  override def createCustomSettings(settings: CodeStyleSettings) =
    new HoconCustomCodeStyleSettings(settings)
}
