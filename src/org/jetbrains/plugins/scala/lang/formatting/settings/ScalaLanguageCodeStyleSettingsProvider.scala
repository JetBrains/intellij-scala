package org.jetbrains.plugins.scala.lang.formatting.settings

import java.lang.String
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType
import com.intellij.lang.Language
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.psi.codeStyle.{CommonCodeStyleSettings, CodeStyleSettingsCustomizable, LanguageCodeStyleSettingsProvider}

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  def getCodeSample(settingsType: SettingsType): String = {
    settingsType match {
      case SettingsType.BLANK_LINES_SETTINGS => GENERAL_CODE_SAMPLE //todo:
      case SettingsType.LANGUAGE_SPECIFIC => GENERAL_CODE_SAMPLE //todo:
      case SettingsType.SPACING_SETTINGS => GENERAL_CODE_SAMPLE //todo:
      case SettingsType.WRAPPING_AND_BRACES_SETTINGS => WRAPPING_AND_BRACES_SAMPLE
    }
  }

  def getLanguage: Language = ScalaFileType.SCALA_LANGUAGE

  override def customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType): Unit = {
    def showCustomOption(fieldName: String, titile: String, groupName: String, options: AnyRef*) {
      consumer.showCustomOption(classOf[ScalaCodeStyleSettings], fieldName, titile, groupName, options: _*)
    }
    //Binary expression section
    consumer.showStandardOptions("BINARY_OPERATION_WRAP", "ALIGN_MULTILINE_BINARY_OPERATION",
      "ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION", "PARENTHESES_EXPRESSION_LPAREN_WRAP",
      "PARENTHESES_EXPRESSION_RPAREN_WRAP")
    consumer.renameStandardOption("BINARY_OPERATION_WRAP", "Wrap infix expressions, patterns and types ")

    //Method calls section
    consumer.showStandardOptions("CALL_PARAMETERS_WRAP", "ALIGN_MULTILINE_PARAMETERS_IN_CALLS",
      "PREFER_PARAMETERS_WRAP", "CALL_PARAMETERS_LPAREN_ON_NEXT_LINE", "CALL_PARAMETERS_RPAREN_ON_NEXT_LINE")
  }

  override def getDefaultCommonSettings: CommonCodeStyleSettings = null

  private val GENERAL_CODE_SAMPLE =
    "class A {\n" +
            "  def foo(): Int = 1\n" +
            "}"

  private val WRAPPING_AND_BRACES_SAMPLE =
    "class A {\n  def foo {\n" +
            "    val infixExpr = 1 + 2 + (3 + 4) + 5 + 6 +\n" +
            "      7 + 8 + 9 + 10 + 11 + 12 + 13 + (14 +\n" +
            "      15) + 16 + 17 * 18 + 19 + 20\n" +
            "  }\n" +
            "}"
}