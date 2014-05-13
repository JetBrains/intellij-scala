package intellijhocon.codestyle

import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.psi.codeStyle.CodeStyleSettings
import intellijhocon.lang.HoconLanguage

class HoconTabbedLanguageCodeStylePanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings)
  extends TabbedLanguageCodeStylePanel(HoconLanguage, currentSettings, settings) {

  override protected def addBlankLinesTab(settings: CodeStyleSettings) = () // no blank lines tab
}
