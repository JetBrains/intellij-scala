package intellijhocon.formatting

import com.intellij.psi.codeStyle.{DisplayPriority, CodeStyleSettings, CodeStyleSettingsProvider}
import com.intellij.application.options.{CodeStyleAbstractConfigurable, TabbedLanguageCodeStylePanel}
import intellijhocon.lang.HoconLanguage

class HoconCodeStyleSettingsProvider extends CodeStyleSettingsProvider {

  override def getConfigurableDisplayName = "HOCON"

  override def getPriority = DisplayPriority.COMMON_SETTINGS

  def createSettingsPage(settings: CodeStyleSettings, originalSettings: CodeStyleSettings) =
    new CodeStyleAbstractConfigurable(settings, originalSettings, "HOCON") {
      override protected def createPanel(settings: CodeStyleSettings) =
        new TabbedLanguageCodeStylePanel(HoconLanguage, getCurrentSettings, settings) {}

      def getHelpTopic = null

    }

}
