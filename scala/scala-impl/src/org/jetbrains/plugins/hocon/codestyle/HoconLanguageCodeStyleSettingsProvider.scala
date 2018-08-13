package org.jetbrains.plugins.hocon.codestyle

import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType
import com.intellij.psi.codeStyle.{CodeStyleSettingsCustomizable, CommonCodeStyleSettings, DisplayPriority, LanguageCodeStyleSettingsProvider}
import org.jetbrains.plugins.hocon.lang.HoconLanguage

class HoconLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  override def getLanguage = HoconLanguage

  override def getDisplayPriority = DisplayPriority.COMMON_SETTINGS

  private val ObjectsWrap = "Objects"
  private val ListsWrap = "Lists"
  private val ObjectFieldsWithColonWrap = "Object fields with ':'"
  private val ObjectFieldsWithAssignmentWrap = "Object fields with '=' or '+='"

  override def customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
    def showCustomOption(name: String, title: String, group: String, options: AnyRef*) =
      consumer.showCustomOption(classOf[HoconCustomCodeStyleSettings], name, title, group, options: _*)

    import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable._
    import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType._

    settingsType match {
      case SPACING_SETTINGS =>
        import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable.SpacingOption._

        consumer.showStandardOptions(List(
          SPACE_WITHIN_BRACES,
          SPACE_WITHIN_BRACKETS,
          SPACE_WITHIN_METHOD_CALL_PARENTHESES,
          SPACE_BEFORE_COMMA,
          SPACE_AFTER_COMMA
        ).map(_.name): _*)

        consumer.renameStandardOption(SPACE_WITHIN_BRACES.name, "Object braces")
        consumer.renameStandardOption(SPACE_WITHIN_METHOD_CALL_PARENTHESES.name, "Include qualifier parentheses")

        showCustomOption("SPACE_BEFORE_COLON", "Before colon", SPACES_AROUND_OPERATORS)
        showCustomOption("SPACE_AFTER_COLON", "After colon", SPACES_AROUND_OPERATORS)
        showCustomOption("SPACE_BEFORE_ASSIGNMENT", "Before assignment ('=' and '+=')", SPACES_AROUND_OPERATORS)
        showCustomOption("SPACE_AFTER_ASSIGNMENT", "After assignment ('=' and '+=')", SPACES_AROUND_OPERATORS)
        showCustomOption("SPACE_BEFORE_LBRACE_AFTER_PATH", "Immediately after path expression", SPACES_BEFORE_LEFT_BRACE)
        showCustomOption("SPACE_WITHIN_SUBSTITUTION_BRACES", "Substitution braces", SPACES_WITHIN)
        showCustomOption("SPACE_AFTER_QMARK", "After '?'", SPACES_OTHER)

      case WRAPPING_AND_BRACES_SETTINGS =>
        import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable.WrappingOrBraceOption._

        consumer.showStandardOptions(KEEP_LINE_BREAKS.name)

        showCustomOption("HASH_COMMENTS_AT_FIRST_COLUMN", "Hash comments at first column", WRAPPING_KEEP)
        showCustomOption("DOUBLE_SLASH_COMMENTS_AT_FIRST_COLUMN", "Double slash comments at first column", WRAPPING_KEEP)

        showCustomOption("OBJECTS_WRAP", ObjectsWrap, null, WRAP_OPTIONS, WRAP_VALUES)
        showCustomOption("OBJECTS_ALIGN_WHEN_MULTILINE", "Align when multiline", ObjectsWrap)
        showCustomOption("OBJECTS_NEW_LINE_AFTER_LBRACE", "New line after '{'", ObjectsWrap)
        showCustomOption("OBJECTS_RBRACE_ON_NEXT_LINE", "Place '}' on new line", ObjectsWrap)

        showCustomOption("LISTS_WRAP", ListsWrap, null, WRAP_OPTIONS, WRAP_VALUES)
        showCustomOption("LISTS_ALIGN_WHEN_MULTILINE", "Align when multiline", ListsWrap)
        showCustomOption("LISTS_NEW_LINE_AFTER_LBRACKET", "New line after '['", ListsWrap)
        showCustomOption("LISTS_RBRACKET_ON_NEXT_LINE", "Place ']' on new line", ListsWrap)

        showCustomOption("OBJECT_FIELDS_WITH_COLON_WRAP", ObjectFieldsWithColonWrap, null,
          WRAP_OPTIONS_FOR_SINGLETON, WRAP_VALUES_FOR_SINGLETON)
        showCustomOption("OBJECT_FIELDS_COLON_ON_NEXT_LINE", "Colon on next line", ObjectFieldsWithColonWrap)

        showCustomOption("OBJECT_FIELDS_WITH_ASSIGNMENT_WRAP", ObjectFieldsWithAssignmentWrap, null,
          WRAP_OPTIONS_FOR_SINGLETON, WRAP_VALUES_FOR_SINGLETON)
        showCustomOption("OBJECT_FIELDS_ASSIGNMENT_ON_NEXT_LINE", "Assignment operator on next line", ObjectFieldsWithAssignmentWrap)

        showCustomOption("INCLUDED_RESOURCE_WRAP", "Included resource", null,
          WRAP_OPTIONS_FOR_SINGLETON, WRAP_VALUES_FOR_SINGLETON)

      case BLANK_LINES_SETTINGS =>

        showCustomOption("KEEP_BLANK_LINES_IN_OBJECTS", "In objects", BLANK_LINES_KEEP)
        showCustomOption("KEEP_BLANK_LINES_BEFORE_RBRACE", "Before '}'", BLANK_LINES_KEEP)
        showCustomOption("KEEP_BLANK_LINES_IN_LISTS", "In lists", BLANK_LINES_KEEP)
        showCustomOption("KEEP_BLANK_LINES_BEFORE_RBRACKET", "Before ']'", BLANK_LINES_KEEP)

      case _ =>

    }

  }

  override def getDefaultCommonSettings: CommonCodeStyleSettings = {
    val commonCodeStyleSettings = new CommonCodeStyleSettings(getLanguage)
    val indentOptions = commonCodeStyleSettings.initIndentOptions
    indentOptions.INDENT_SIZE = 2
    indentOptions.TAB_SIZE = 2
    indentOptions.CONTINUATION_INDENT_SIZE = 2
    commonCodeStyleSettings
  }

  override def getIndentOptionsEditor = new SmartIndentOptionsEditor

  def getCodeSample(settingsType: SettingsType): String = settingsType match {
    case SettingsType.INDENT_SETTINGS =>
      """object {
        |  key = value
        |  some.path: 42
        |  list = [
        |    something here
        |    more stuff
        |  ]
        |  some.very.long.path =
        |    very very very long value
        |}
        | """.stripMargin.trim

    case SettingsType.SPACING_SETTINGS =>
      """include file("application.conf")
        |
        |object {
        |  quix: 42
        |  foo.bar = stuff
        |  obj = {key: value, kye: vlaue}
        |  list = [1, 2, 3]
        |  subst = ${some.path}
        |  optsubst = ${?some.path}
        |}
        | """.stripMargin.trim

    case SettingsType.WRAPPING_AND_BRACES_SETTINGS =>
      """include "someExtraordinarilyLongName"
        |
        |object {
        |  #comment
        |  key = value
        |  simplelist = [element]
        |  simpleobj = {k: v}
        |  longlist = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
        |  longobj = {key: value, foo: bar, stuff: 42, quix: 3.14}
        |  some.path: long long long long long long value
        |  another.path = another very very very very long value
        |  anotherobj {key: value, foo: bar, stuff: 42, quix: 3.14}
        |
        |#comment originally at first column
        |//comment originally at first column
        |
        |}
        | """.stripMargin.trim

    case SettingsType.BLANK_LINES_SETTINGS =>
      """include "application"
        |
        |
        |object {
        |  key: value
        |
        |  num = 42
        |
        |}
        |
        |list = [
        |  value
        |
        |  another one
        |
        |]
        | """.stripMargin.trim

    case _ => ""
  }
}
