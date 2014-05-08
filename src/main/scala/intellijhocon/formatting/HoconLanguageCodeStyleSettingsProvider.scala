package intellijhocon.formatting

import com.intellij.psi.codeStyle.{DisplayPriority, CommonCodeStyleSettings, LanguageCodeStyleSettingsProvider}
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType
import com.intellij.application.options.SmartIndentOptionsEditor
import intellijhocon.lang.HoconLanguage

class HoconLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  def getLanguage = HoconLanguage

  override def getDisplayPriority = DisplayPriority.COMMON_SETTINGS

  override def getDefaultCommonSettings = {
    val commonCodeStyleSettings = new CommonCodeStyleSettings(getLanguage)
    val indentOptions = commonCodeStyleSettings.initIndentOptions
    indentOptions.INDENT_SIZE = 2
    indentOptions.TAB_SIZE = 2
    indentOptions.CONTINUATION_INDENT_SIZE = 2
    commonCodeStyleSettings
  }

  override def getIndentOptionsEditor = new SmartIndentOptionsEditor

  def getCodeSample(settingsType: SettingsType) = "hocony { hueh = lol }"
}
