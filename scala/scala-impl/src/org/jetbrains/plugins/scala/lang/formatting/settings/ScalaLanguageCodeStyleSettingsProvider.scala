package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable.OptionAnchor
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType
import com.intellij.psi.codeStyle.{CodeStyleSettingsCustomizable, CommonCodeStyleSettings, DisplayPriority, LanguageCodeStyleSettingsProvider}
import org.jetbrains.plugins.scala.ScalaLanguage

import scala.collection.mutable.ArrayBuffer

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  def getCodeSample(settingsType: SettingsType): String = {
    settingsType match {
      case SettingsType.BLANK_LINES_SETTINGS => BLANK_LINES_CODE_SAMPLE
      case SettingsType.LANGUAGE_SPECIFIC => GENERAL_CODE_SAMPLE //todo:
      case SettingsType.SPACING_SETTINGS => GENERAL_CODE_SAMPLE //todo:
      case SettingsType.WRAPPING_AND_BRACES_SETTINGS => WRAPPING_AND_BRACES_SAMPLE
      case _ => GENERAL_CODE_SAMPLE //todo:
    }
  }

  override def getLanguage: Language = ScalaLanguage.INSTANCE

  override def customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
    def showCustomOption(fieldName: String, title: String, groupName: String, options: AnyRef*) {
      consumer.showCustomOption(classOf[ScalaCodeStyleSettings], fieldName, title, groupName, options: _*)
    }

    def showCustomOptionAnchored(fieldName: String, title: String, groupName: String,
                         anchorOpt: OptionAnchor, anchorField: String, options: AnyRef*): Unit = {
      consumer.showCustomOption(classOf[ScalaCodeStyleSettings], fieldName, title, groupName, anchorOpt, anchorField, options:_*)
    }

    val buffer: ArrayBuffer[String] = new ArrayBuffer
    //spacing
    if (settingsType == SettingsType.SPACING_SETTINGS) {
      buffer ++= Seq("SPACE_AFTER_COMMA", "SPACE_BEFORE_IF_PARENTHESES", "SPACE_BEFORE_FOR_PARENTHESES",
        "SPACE_BEFORE_METHOD_PARENTHESES", "SPACE_BEFORE_METHOD_CALL_PARENTHESES", "SPACE_WITHIN_FOR_PARENTHESES",
        "SPACE_WITHIN_IF_PARENTHESES", "SPACE_WITHIN_WHILE_PARENTHESES", "SPACE_WITHIN_PARENTHESES",
        "SPACE_WITHIN_METHOD_PARENTHESES", "SPACE_WITHIN_METHOD_CALL_PARENTHESES", "SPACE_WITHIN_BRACKETS",
        "SPACE_BEFORE_CLASS_LBRACE", "SPACE_BEFORE_METHOD_LBRACE", "SPACE_BEFORE_IF_LBRACE",
        "SPACE_BEFORE_WHILE_LBRACE", "SPACE_BEFORE_DO_LBRACE", "SPACE_BEFORE_FOR_LBRACE", "SPACE_BEFORE_TRY_LBRACE",
        "SPACE_BEFORE_CATCH_LBRACE", "SPACE_BEFORE_FINALLY_LBRACE", "SPACE_BEFORE_WHILE_PARENTHESES",
        "SPACE_AFTER_SEMICOLON", "SPACE_BEFORE_ELSE_LBRACE", "SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES",
        "SPACE_BEFORE_TYPE_PARAMETER_LIST")
    }

    //blank lines
    if (settingsType == SettingsType.BLANK_LINES_SETTINGS) {
      buffer ++= Seq("KEEP_BLANK_LINES_IN_CODE", "BLANK_LINES_AFTER_CLASS_HEADER",
        "KEEP_BLANK_LINES_BEFORE_RBRACE", "KEEP_BLANK_LINES_IN_DECLARATIONS", "BLANK_LINES_BEFORE_PACKAGE",
        "BLANK_LINES_AFTER_PACKAGE", "BLANK_LINES_BEFORE_IMPORTS", "BLANK_LINES_AFTER_IMPORTS",
        "BLANK_LINES_AROUND_CLASS", "BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER", "BLANK_LINES_AROUND_FIELD_IN_INTERFACE",
        "BLANK_LINES_AROUND_FIELD", "BLANK_LINES_AROUND_METHOD_IN_INTERFACE", "BLANK_LINES_AROUND_METHOD",
        "BLANK_LINES_BEFORE_METHOD_BODY")
    }

    if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
      consumer.renameStandardOption("BINARY_OPERATION_WRAP", "Wrap infix expressions, patterns and types ")
      consumer.renameStandardOption(CodeStyleSettingsCustomizable.WRAPPING_SWITCH_STATEMENT,
        "'match' or 'switch' statements")

      //Binary expression section
      buffer ++= Seq("BINARY_OPERATION_WRAP", "ALIGN_MULTILINE_BINARY_OPERATION",
        "ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION", "PARENTHESES_EXPRESSION_LPAREN_WRAP",
        "PARENTHESES_EXPRESSION_RPAREN_WRAP")

      //Method calls section
      buffer ++= Seq("CALL_PARAMETERS_WRAP", "ALIGN_MULTILINE_PARAMETERS_IN_CALLS",
        "PREFER_PARAMETERS_WRAP", "CALL_PARAMETERS_RPAREN_ON_NEXT_LINE")

      //align call parameters
      buffer ++= Seq("ALIGN_MULTILINE_METHOD_BRACKETS")

      //method call chain
      buffer ++= Seq("METHOD_CALL_CHAIN_WRAP", "ALIGN_MULTILINE_CHAINED_METHODS", "KEEP_LINE_BREAKS")

      //brace placement
      buffer ++= Seq("CLASS_BRACE_STYLE", "METHOD_BRACE_STYLE", "BRACE_STYLE")

      //extends list wrap
      buffer ++= Seq("EXTENDS_LIST_WRAP", "EXTENDS_KEYWORD_WRAP")

      //method parameters
      buffer ++= Seq("METHOD_PARAMETERS_WRAP", "ALIGN_MULTILINE_PARAMETERS", "METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE",
        "METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE")

      //if statement
      buffer ++= Seq("IF_BRACE_FORCE", "ELSE_ON_NEW_LINE", "SPECIAL_ELSE_IF_TREATMENT")

      //brace forces
      buffer ++= Seq("FOR_BRACE_FORCE", "WHILE_BRACE_FORCE", "DOWHILE_BRACE_FORCE", "WHILE_ON_NEW_LINE",
        "INDENT_CASE_FROM_SWITCH", "CATCH_ON_NEW_LINE", "FINALLY_ON_NEW_LINE", "FOR_STATEMENT_WRAP",
        "ALIGN_MULTILINE_FOR", "FOR_STATEMENT_LPAREN_ON_NEXT_LINE", "FOR_STATEMENT_RPAREN_ON_NEXT_LINE")

      //modifier list wrap
      buffer ++= Seq("MODIFIER_LIST_WRAP")

      //align in colums
      buffer ++= Seq("ALIGN_GROUP_FIELD_DECLARATIONS")

      buffer ++= Seq("WRAP_LONG_LINES")

      //annotations wrap
      buffer ++= Seq("CLASS_ANNOTATION_WRAP", "METHOD_ANNOTATION_WRAP", "FIELD_ANNOTATION_WRAP",
        "PARAMETER_ANNOTATION_WRAP", "VARIABLE_ANNOTATION_WRAP")

      buffer ++= Seq("KEEP_SIMPLE_BLOCKS_IN_ONE_LINE", "KEEP_SIMPLE_METHODS_IN_ONE_LINE", "KEEP_FIRST_COLUMN_COMMENT")
    }

    //comments generation
    if (settingsType == SettingsType.COMMENTER_SETTINGS) {
      buffer ++= Seq("LINE_COMMENT_AT_FIRST_COLUMN", "BLOCK_COMMENT_AT_FIRST_COLUMN")
    }

    consumer.showStandardOptions(buffer.toArray:_*)
    consumer.renameStandardOption("SPACE_BEFORE_TYPE_PARAMETER_LIST", "Before opening square bracket")

    //Custom options
    if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
      showCustomOption("ALIGN_EXTENDS_WITH", ApplicationBundle.message("wrapping.align.when.multiline"),
        ApplicationBundle.message("wrapping.extends.implements.list"), ScalaCodeStyleSettings.EXTENDS_ALIGN_STRING,
        ScalaCodeStyleSettings.EXTENDS_ALIGN_VALUES)
      showCustomOption("WRAP_BEFORE_WITH_KEYWORD", "Wrap before 'with' keyword",
        ApplicationBundle.message("wrapping.extends.implements.list"))
      showCustomOption("ALIGN_IF_ELSE", "Align if-else statements", ApplicationBundle.message("wrapping.if.statement"))
      showCustomOption("METHOD_BRACE_FORCE", "Force braces", METHOD_DEFINITION,
        CodeStyleSettingsCustomizable.BRACE_OPTIONS, CodeStyleSettingsCustomizable.BRACE_VALUES)
      showCustomOption("TRY_BRACE_FORCE", "Force 'try' braces",
        CodeStyleSettingsCustomizable.WRAPPING_TRY_STATEMENT, CodeStyleSettingsCustomizable.BRACE_OPTIONS,
        CodeStyleSettingsCustomizable.BRACE_VALUES)
      showCustomOption("FINALLY_BRACE_FORCE", "Force 'finally' braces",
        CodeStyleSettingsCustomizable.WRAPPING_TRY_STATEMENT, CodeStyleSettingsCustomizable.BRACE_OPTIONS,
        CodeStyleSettingsCustomizable.BRACE_VALUES)
      showCustomOption("CLOSURE_BRACE_FORCE", "Force braces", ANONYMOUS_METHOD,
        CodeStyleSettingsCustomizable.BRACE_OPTIONS, CodeStyleSettingsCustomizable.BRACE_VALUES)
      showCustomOption("CASE_CLAUSE_BRACE_FORCE", "Force 'case' branch braces",
        CodeStyleSettingsCustomizable.WRAPPING_SWITCH_STATEMENT, CodeStyleSettingsCustomizable.BRACE_OPTIONS,
        CodeStyleSettingsCustomizable.BRACE_VALUES)
      showCustomOption("PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE", "Parameters on new line", ANONYMOUS_METHOD)
      showCustomOption("NOT_CONTINUATION_INDENT_FOR_PARAMS", "Use normal indent for parameters",
        CodeStyleSettingsCustomizable.WRAPPING_METHOD_PARAMETERS)
      showCustomOption("ALIGN_TYPES_IN_MULTILINE_DECLARATIONS", "Align parameter types in multiline declarations",
        CodeStyleSettingsCustomizable.WRAPPING_METHOD_PARAMETERS)
      showCustomOption("INDENT_FIRST_PARAMETER_CLAUSE", "Indent first parameter clause",
        CodeStyleSettingsCustomizable.WRAPPING_METHOD_PARAMETERS)
      showCustomOption("DO_NOT_INDENT_CASE_CLAUSE_BODY", "Do not indent case clause body", CodeStyleSettingsCustomizable.WRAPPING_SWITCH_STATEMENT)
      showCustomOption("INDENT_BRACED_FUNCTION_ARGS", "Indent braced arguments", CodeStyleSettingsCustomizable.WRAPPING_METHOD_ARGUMENTS_WRAPPING)
      showCustomOption("ALIGN_IN_COLUMNS_CASE_BRANCH", "Align in columns 'case' branches",
        CodeStyleSettingsCustomizable.WRAPPING_SWITCH_STATEMENT)
      showCustomOption("ALIGN_COMPOSITE_PATTERN", "Align multiline pattern alternatives",
        CodeStyleSettingsCustomizable.WRAPPING_BINARY_OPERATION)
      showCustomOption("PLACE_SELF_TYPE_ON_NEW_LINE", "Place self type on new line", CLASS_DEFINITION)
      showCustomOption("KEEP_XML_FORMATTING", "Keep xml formatting", XML_FORMATTING)
      showCustomOption("KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST", "Do not format one-line lambdas in arg list",
        CodeStyleSettingsCustomizable.WRAPPING_KEEP)
      showCustomOption("DO_NOT_ALIGN_BLOCK_EXPR_PARAMS", "Do not align block expression parameters",
        CodeStyleSettingsCustomizable.WRAPPING_METHOD_ARGUMENTS_WRAPPING)
      showCustomOption("DO_NOT_INDENT_TUPLES_CLOSE_BRACE", "Do not indent tuples closing parenthesis", TUPLES_WRAP)
      showCustomOption("ALIGN_TUPLE_ELEMENTS", "Align tuple elements", TUPLES_WRAP)
      showCustomOptionAnchored("CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN", ApplicationBundle.message("wrapping.new.line.after.lpar"),
        CodeStyleSettingsCustomizable.WRAPPING_METHOD_ARGUMENTS_WRAPPING, OptionAnchor.BEFORE, "CALL_PARAMETERS_RPAREN_ON_NEXT_LINE",
        Array("No new line", "New line always", "New line for multiple arguments"), Array(ScalaCodeStyleSettings.NO_NEW_LINE,
          ScalaCodeStyleSettings.NEW_LINE_ALWAYS, ScalaCodeStyleSettings.NEW_LINE_FOR_MULTIPLE_ARGUMENTS))
    }

    if (settingsType == SettingsType.SPACING_SETTINGS) {
      showCustomOption("SPACE_AFTER_MODIFIERS_CONSTRUCTOR", "Constructor parameters with modifiers",
        CodeStyleSettingsCustomizable.SPACES_BEFORE_PARENTHESES)
      showCustomOption("SPACE_AFTER_TYPE_COLON", "Space after colon, before declarations' type",
        CodeStyleSettingsCustomizable.SPACES_OTHER)
      showCustomOption("SPACE_BEFORE_TYPE_COLON", "Space before colon, after declarations' name",
        CodeStyleSettingsCustomizable.SPACES_OTHER)
      showCustomOption("SPACE_INSIDE_CLOSURE_BRACES", "Space inside closure braces",
        CodeStyleSettingsCustomizable.SPACES_OTHER)
      showCustomOption("SPACE_BEFORE_INFIX_METHOD_CALL_PARENTHESES", "Space before infix method call parentheses",
        CodeStyleSettingsCustomizable.SPACES_BEFORE_PARENTHESES)
      showCustomOption("SPACES_IN_ONE_LINE_BLOCKS", "Insert whitespaces in simple one line blocks", CodeStyleSettingsCustomizable.SPACES_OTHER)
      showCustomOption("SPACE_BEFORE_INFIX_LIKE_METHOD_PARENTHESES", "Space before infix method parentheses",
        CodeStyleSettingsCustomizable.SPACES_BEFORE_PARENTHESES)
      showCustomOption("PRESERVE_SPACE_AFTER_METHOD_DECLARATION_NAME", "Preserve space before method parentheses",
        CodeStyleSettingsCustomizable.SPACES_BEFORE_PARENTHESES)
      showCustomOption("SPACE_BEFORE_BRACE_METHOD_CALL", "Space before method call brace",
        CodeStyleSettingsCustomizable.SPACES_BEFORE_PARENTHESES)
      showCustomOption("SPACES_IN_IMPORTS", "Spaces after open and before close braces in imports",
        CodeStyleSettingsCustomizable.SPACES_OTHER)
      showCustomOption("SPACES_AROUND_AT_IN_PATTERNS", "Spaces around '@' in pattern bindings",
        CodeStyleSettingsCustomizable.SPACES_OTHER)
      showCustomOption("NEWLINE_AFTER_ANNOTATIONS", "Newline after annotations", CodeStyleSettingsCustomizable.SPACES_OTHER)
      showCustomOption("KEEP_COMMENTS_ON_SAME_LINE", "Keep one-line comments on same line", CodeStyleSettingsCustomizable.SPACES_OTHER)
      showCustomOption("SPACE_BEFORE_TYPE_PARAMETER_IN_DEF_LIST", "Before opening square bracket", CodeStyleSettingsCustomizable.SPACES_IN_TYPE_PARAMETERS)
      showCustomOption("SPACE_INSIDE_SELF_TYPE_BRACES", "Self type braces", CodeStyleSettingsCustomizable.SPACES_WITHIN)
    }

    if (settingsType == SettingsType.BLANK_LINES_SETTINGS) {
      showCustomOption("BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES", "Around method in inner scopes", CodeStyleSettingsCustomizable.BLANK_LINES)
      showCustomOption("BLANK_LINES_AROUND_FIELD_IN_INNER_SCOPES", "Around field in inner scopes", CodeStyleSettingsCustomizable.BLANK_LINES)
    }

    if (settingsType == SettingsType.LANGUAGE_SPECIFIC) {
      showCustomOption("SD_ALIGN_PARAMETERS_COMMENTS", "Align parameter descriptions", ScalaDocFormattingPanel.ALIGNMENT_GROUP)
      showCustomOption("SD_ALIGN_EXCEPTION_COMMENTS", "Align throws exception descriptions", ScalaDocFormattingPanel.ALIGNMENT_GROUP)
      showCustomOption("SD_ALIGN_RETURN_COMMENTS", "Align return value description", ScalaDocFormattingPanel.ALIGNMENT_GROUP)
      showCustomOption("SD_ALIGN_OTHER_TAGS_COMMENTS", "Align other tags descriptions", ScalaDocFormattingPanel.ALIGNMENT_GROUP)

      showCustomOption("SD_KEEP_BLANK_LINES_BETWEEN_TAGS", "Keep (blank lines between tags will not be removed)", ScalaDocFormattingPanel.BLANK_LINES_GROUP)
      showCustomOption("SD_BLANK_LINE_BEFORE_TAGS", ApplicationBundle.message("checkbox.after.description"), ScalaDocFormattingPanel.BLANK_LINES_GROUP)
      showCustomOption("SD_BLANK_LINE_AFTER_PARAMETERS_COMMENTS", ApplicationBundle.message("checkbox.after.parameter.descriptions"), ScalaDocFormattingPanel.BLANK_LINES_GROUP)
      showCustomOption("SD_BLANK_LINE_AFTER_RETURN_COMMENTS",  ApplicationBundle.message("checkbox.after.return.tag"), ScalaDocFormattingPanel.BLANK_LINES_GROUP)
      showCustomOption("SD_BLANK_LINE_BETWEEN_PARAMETERS", "Between parameter descriptions", ScalaDocFormattingPanel.BLANK_LINES_GROUP)
      showCustomOption("SD_BLANK_LINE_BEFORE_PARAMETERS", "Before parameter descriptions", ScalaDocFormattingPanel.BLANK_LINES_GROUP)

      showCustomOption("SD_PRESERVE_SPACES_IN_TAGS", "Preserve spaces in tags", ScalaDocFormattingPanel.OTHER_GROUP)
    }

  }

  //custom groups
  private val METHOD_DEFINITION = "Method definition"
  private val ANONYMOUS_METHOD = "Anonymous method definition"
  private val CLASS_DEFINITION = "Class definition"
  private val XML_FORMATTING = "Xml formatting"
  private val TUPLES_WRAP = "Tuple"

  override def getDefaultCommonSettings: CommonCodeStyleSettings = {
    val commonCodeStyleSettings: CommonCodeStyleSettings = new CommonCodeStyleSettings(getLanguage)
    val indentOptions: CommonCodeStyleSettings.IndentOptions = commonCodeStyleSettings.initIndentOptions
    indentOptions.INDENT_SIZE = 2
    indentOptions.TAB_SIZE = 2
    indentOptions.CONTINUATION_INDENT_SIZE = 2
    commonCodeStyleSettings.KEEP_FIRST_COLUMN_COMMENT = false //added here to comply with prior default behavior
    commonCodeStyleSettings
  }

  override def getDisplayPriority = DisplayPriority.COMMON_SETTINGS

  override def getIndentOptionsEditor = new SmartIndentOptionsEditor

  private val GENERAL_CODE_SAMPLE =
    """
      |class A {
      |  def foo[A](): Int = 42
      |
      |  foo[Int]( )
      |}
    """.stripMargin.trim

  private val WRAPPING_AND_BRACES_SAMPLE =
    "class A {\n" +
            "  def foo {\n" +
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
            "  goo.foo().foo(1, 2).foo(z = 1, y = 2).foo().foo(1, 2, 3).foo()\n" +
            "  \n" +
            "  def m(x: Int, y: Int, z: Int)(u: Int, f: Int, l: Int) {\n" +
            "    val zz = if (true) 1 else 3\n" +
            "    val uz = if (true)\n" +
            "               1\n" +
            "              else {\n" +
            "              }\n" +
            "    if (true) {\n" +
            "      false\n" +
            "    } else if (false) {\n" +
            "    } else true\n" +
            "    for (i <- 1 to 5) yield i + 1\n" +
            "    Some(3) match {\n" +
            "      case Some(a) if a != 2 => a\n" +
            "      case Some(1) |\n" +
            "         Some(2) => \n" +
            "        \n" +
            "      case _ =>\n" +
            "    }\n" +
            "    try a + 2\n" +
            "    catch {\n" +
            "      case e => (i: Int) => i + 1\n" +
            "    } finally \n" +
            "      doNothing\n" +
            "    while (true) \n" +
            "      true = false\n" +
            "  }\n" +
            "}"

  private val BLANK_LINES_CODE_SAMPLE =
    "//code\npackage A\n" +
            "\n" +
            "\n" +
            "import a.b\n" +
            "\n" +
            "import b.c\n" +
            "import c.d\n" +
            "\n" +
            "\n" +
            "class A {\n" +
            "  def foo = 1\n" +
            "  def goo = 2\n" +
            "  type S = String\n" +
            "  val a = 1\n" +
            "  \n" +
            "  val b = 2\n" +
            "  val c = 2\n" +
            "}\n" +
            "\n" +
            "trait B {\n" +
            "  \n" +
            "  def foo\n" +
            "  def goo\n" +
            "  def too = {\n" +
            "    \n" +
            "    \n" +
            "    val x = 2\n" +
            "    new J {\n" +
            "      def goo = 1\n" +
            "    }\n" +
            "  }\n" +
            "}"
}
