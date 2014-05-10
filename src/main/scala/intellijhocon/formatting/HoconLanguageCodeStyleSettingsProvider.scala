package intellijhocon.formatting

import com.intellij.psi.codeStyle.{CodeStyleSettingsCustomizable, DisplayPriority, CommonCodeStyleSettings, LanguageCodeStyleSettingsProvider}
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType
import com.intellij.application.options.SmartIndentOptionsEditor
import intellijhocon.lang.HoconLanguage

class HoconLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  def getLanguage = HoconLanguage

  override def getDisplayPriority = DisplayPriority.COMMON_SETTINGS

  private val ObjectsWrap = "Objects"
  private val ListsWrap = "Lists"
  private val ObjectFieldsWithColonWrap = "Object fields with ':'"
  private val ObjectFieldsWithAssignmentWrap = "Object fields with '=' or '+='"

  override def customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
    def showCustomOption(name: String, title: String, group: String, options: AnyRef*) =
      consumer.showCustomOption(classOf[HoconCustomCodeStyleSettings], name, title, group, options: _*)

    import CodeStyleSettingsCustomizable._
    import SettingsType._

    settingsType match {
      case SPACING_SETTINGS =>
        import SpacingOption._

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
        showCustomOption("SPACES_WITHIN_REFERENCE_BRACES", "Reference braces", SPACES_WITHIN)
        showCustomOption("SPACE_AFTER_QMARK", "After '?'", SPACES_OTHER)

      case WRAPPING_AND_BRACES_SETTINGS =>
        import WrappingOrBraceOption._

        consumer.showStandardOptions(List(
          KEEP_LINE_BREAKS,
          KEEP_SIMPLE_BLOCKS_IN_ONE_LINE,
          WRAP_LONG_LINES
        ).map(_.name): _*)

        consumer.renameStandardOption(KEEP_SIMPLE_BLOCKS_IN_ONE_LINE.name, "Simple objects in one line")

        showCustomOption("KEEP_SIMPLE_LISTS_IN_ONE_LINE", "Simple lists in one line", WRAPPING_KEEP)
        showCustomOption("HASH_COMMENTS_AT_FIRST_COLUMN", "Hash comments at first column", WRAPPING_KEEP)
        showCustomOption("DOUBLE_SLASH_COMMENTS_AT_FIRST_COLUMN", "Double slash comments at first column", WRAPPING_KEEP)

        showCustomOption("OBJECTS_WRAP", ObjectsWrap, null, WRAP_OPTIONS, WRAP_VALUES)
        showCustomOption("OBJECTS_ALIGN_WHEN_MULTILINE", "Align when multiline", ObjectsWrap)
        showCustomOption("OBJECTS_LBRACE_ON_NEXT_LINE", "New line after '{'", ObjectsWrap)
        showCustomOption("OBJECTS_RBRACE_ON_NEXT_LINE", "Place '}' on new line", ObjectsWrap)

        showCustomOption("LISTS_WRAP", ListsWrap, null, WRAP_OPTIONS, WRAP_VALUES)
        showCustomOption("LISTS_ALIGN_WHEN_MULTILINE", "Align when multiline", ListsWrap)
        showCustomOption("LISTS_LBRACKET_ON_NEXT_LINE", "New line after '['", ListsWrap)
        showCustomOption("LISTS_RBRACKET_ON_NEXT_LINE", "Place ']' on new line", ListsWrap)

        showCustomOption("OBJECT_FIELDS_WITH_COLON_WRAP", ObjectFieldsWithColonWrap, null,
          WRAP_OPTIONS_FOR_SINGLETON, WRAP_VALUES_FOR_SINGLETON)
        showCustomOption("OBJECT_FIELDS_COLON_ON_NEXT_LINE", "Colon on next line", ObjectFieldsWithColonWrap)

        showCustomOption("OBJECT_FIELDS_WITH_ASSIGNMENT_WRAP", ObjectFieldsWithAssignmentWrap, null,
          WRAP_OPTIONS_FOR_SINGLETON, WRAP_VALUES_FOR_SINGLETON)
        showCustomOption("OBJECT_FIELDS_ASSIGNMENT_ON_NEXT_LINE", "Assignment operator on next line", ObjectFieldsWithAssignmentWrap)

      case _ =>

    }

  }

  override def getDefaultCommonSettings = {
    val commonCodeStyleSettings = new CommonCodeStyleSettings(getLanguage)
    val indentOptions = commonCodeStyleSettings.initIndentOptions
    indentOptions.INDENT_SIZE = 2
    indentOptions.TAB_SIZE = 2
    indentOptions.CONTINUATION_INDENT_SIZE = 2
    commonCodeStyleSettings
  }

  override def getIndentOptionsEditor = new SmartIndentOptionsEditor

  def getCodeSample(settingsType: SettingsType) = settingsType match {
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
        | """.stripMargin

    case SettingsType.SPACING_SETTINGS =>
      """include file("application.conf")
        |
        |object {
        |  quix: 42
        |  foo.bar = stuff
        |  obj = {key: value, kye: vlaue}
        |  list = [1, 2, 3]
        |  ref = ${some.path}
        |  optref = ${?some.path}
        |}
        | """.stripMargin

    case SettingsType.WRAPPING_AND_BRACES_SETTINGS =>
      """object {
        |  #hash comment
        |  //double slash comment
        |  key = value
        |  simplelist = [element]
        |  simpleobj = {k: v}
        |  longlist = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
        |  longobj = {key: value, foo: bar, stuff: 42, quix: 3.14}
        |  some.path: long long long long long long value
        |  another.path = another very very very very long value
        |  anotherobj {key: value, foo: bar, stuff: 42, quix: 3.14}
        |}
        | """.stripMargin

    case _ => ""
  }
}
