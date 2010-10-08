package org.jetbrains.plugins.scala.lang.formatting.settings

import java.lang.String
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType
import com.intellij.lang.Language
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.psi.codeStyle.{CommonCodeStyleSettings, CodeStyleSettingsCustomizable, LanguageCodeStyleSettingsProvider}
import collection.mutable.ArrayBuffer

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

    val buffer: ArrayBuffer[String] = new ArrayBuffer
    //Binary expression section
    buffer ++= Array("BINARY_OPERATION_WRAP", "ALIGN_MULTILINE_BINARY_OPERATION",
      "ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION", "PARENTHESES_EXPRESSION_LPAREN_WRAP",
      "PARENTHESES_EXPRESSION_RPAREN_WRAP")
    consumer.renameStandardOption("BINARY_OPERATION_WRAP", "Wrap infix expressions, patterns and types ")

    //Method calls section
    buffer ++= Array("CALL_PARAMETERS_WRAP", "ALIGN_MULTILINE_PARAMETERS_IN_CALLS",
      "PREFER_PARAMETERS_WRAP", "CALL_PARAMETERS_LPAREN_ON_NEXT_LINE", "CALL_PARAMETERS_RPAREN_ON_NEXT_LINE")

    //align call parameters
    buffer ++= Array("ALIGN_MULTILINE_METHOD_BRACKETS")

    //method call chain
    buffer ++= Array("METHOD_CALL_CHAIN_WRAP", "ALIGN_MULTILINE_CHAINED_METHODS")

    //blank lines
    buffer ++= Array("KEEP_BLANK_LINES_IN_CODE", "KEEP_LINE_BREAKS", "BLANK_LINES_AFTER_CLASS_HEADER",
      "KEEP_BLANK_LINES_BEFORE_RBRACE")

    //brace placement
    buffer ++= Array("CLASS_BRACE_STYLE", "METHOD_BRACE_STYLE", "BRACE_STYLE")

    consumer.showStandardOptions(buffer.toArray:_*)
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
            "\n" +
            "  class Foo {\n" +
            "    def foo(x: Int = 0, y: Int = 1, z: Int = 2) = new Foo\n" +
            "  }\n" +
            "  \n" +
            "  val goo = new Foo\n" +
            "\n" +
            "  goo.foo().foo(1, 2).foo(z = 1, y = 2).foo().foo(1, 2, 3).foo()" +
            "}"
}