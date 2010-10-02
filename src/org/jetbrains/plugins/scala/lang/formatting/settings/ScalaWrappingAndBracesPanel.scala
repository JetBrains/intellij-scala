package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.application.options.codeStyle.OptionTableWithPreviewPanel
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType
import com.intellij.psi.codeStyle.{LanguageCodeStyleSettingsProvider, CodeStyleSettings}
import com.intellij.openapi.application.ApplicationBundle
import java.lang.String
import org.jetbrains.plugins.scala.ScalaFileType

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaWrappingAndBracesPanel(settings: CodeStyleSettings) extends OptionTableWithPreviewPanel(settings) {
  //constructor body
  setPanelLanguage(ScalaFileType.SCALA_LANGUAGE)
  init
  //end of constructor body

  def getSettingsType: SettingsType = {
    return LanguageCodeStyleSettingsProvider.SettingsType.WRAPPING_AND_BRACES_SETTINGS
  }

  def initTables: Unit = {
  }

  override def getPreviewText: String = {
"""
class A {
  def foo {
    val result = 1 + 2 + 3 + 4 + 5 + 6 +
      7 + 8 + 9 + 10 + 11 + 12 + 13 + 14 +
      15 + 16 + 17 + 18 + 19 + 20
  }
}
"""
  }
}