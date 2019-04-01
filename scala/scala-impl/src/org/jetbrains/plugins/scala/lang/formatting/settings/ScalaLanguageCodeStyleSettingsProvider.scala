package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable.OptionAnchor
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType
import com.intellij.psi.codeStyle.{CodeStyleSettingsCustomizable, CommonCodeStyleSettings, DisplayPriority, LanguageCodeStyleSettingsProvider}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaLanguageCodeStyleSettingsProvider._

import scala.collection.mutable.ArrayBuffer

class ScalaLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  def getCodeSample(settingsType: SettingsType): String = {
    settingsType match {
      case SettingsType.INDENT_SETTINGS => IndentsCodeSample
      case SettingsType.SPACING_SETTINGS => SpacingCodeSample
      case SettingsType.WRAPPING_AND_BRACES_SETTINGS => WrappingAndBracesSample
      case SettingsType.BLANK_LINES_SETTINGS => BlankLinesCodeSample
      // TODO: looks like other setting types are not displayed for now
      case SettingsType.LANGUAGE_SPECIFIC => GeneralCodeSample
      case _ => GeneralCodeSample
    }
  }

  override def getLanguage: Language = ScalaLanguage.INSTANCE

  override def customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
    val buffer: ArrayBuffer[String] = new ArrayBuffer

    //spacing
    if (settingsType == SettingsType.SPACING_SETTINGS) {
      consumer.renameStandardOption("SPACE_BEFORE_TYPE_PARAMETER_LIST", "Before opening square bracket")

      buffer ++= Seq(
        // After
        "SPACE_AFTER_COMMA",
        "SPACE_AFTER_SEMICOLON",

        // Before
        "SPACE_BEFORE_METHOD_PARENTHESES",
        "SPACE_BEFORE_METHOD_CALL_PARENTHESES",
        "SPACE_BEFORE_CLASS_LBRACE",
        "SPACE_BEFORE_METHOD_LBRACE",
        "SPACE_BEFORE_TYPE_PARAMETER_LIST",

        // Within
        "SPACE_WITHIN_FOR_PARENTHESES",
        "SPACE_WITHIN_IF_PARENTHESES",
        "SPACE_WITHIN_WHILE_PARENTHESES",
        "SPACE_WITHIN_PARENTHESES",
        "SPACE_WITHIN_METHOD_PARENTHESES",
        "SPACE_WITHIN_METHOD_CALL_PARENTHESES",
        "SPACE_WITHIN_BRACKETS",
        "SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES",
      )
    }

    //blank lines
    if (settingsType == SettingsType.BLANK_LINES_SETTINGS) {
      buffer ++= Seq(
        "KEEP_BLANK_LINES_IN_CODE",
        "BLANK_LINES_AFTER_CLASS_HEADER",
        "KEEP_BLANK_LINES_BEFORE_RBRACE",
        "KEEP_BLANK_LINES_IN_DECLARATIONS",
        "BLANK_LINES_BEFORE_PACKAGE",
        "BLANK_LINES_AFTER_PACKAGE",
        "BLANK_LINES_BEFORE_IMPORTS",
        "BLANK_LINES_AFTER_IMPORTS",
        "BLANK_LINES_AROUND_CLASS",
        "BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER",
        "BLANK_LINES_AROUND_FIELD_IN_INTERFACE",
        "BLANK_LINES_AROUND_FIELD",
        "BLANK_LINES_AROUND_METHOD_IN_INTERFACE",
        "BLANK_LINES_AROUND_METHOD",
        "BLANK_LINES_BEFORE_METHOD_BODY")
    }

    //wrapping and Braces
    if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
      consumer.renameStandardOption("BINARY_OPERATION_WRAP", "Infix expressions")
      consumer.renameStandardOption("EXTENDS_LIST_WRAP", "Extends/with list")
      consumer.renameStandardOption("EXTENDS_KEYWORD_WRAP", "Extends keyword")

      //Binary expression section
      buffer ++= Seq(
        "BINARY_OPERATION_WRAP",
        "ALIGN_MULTILINE_BINARY_OPERATION",
        "ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION",
        "PARENTHESES_EXPRESSION_LPAREN_WRAP",
        "PARENTHESES_EXPRESSION_RPAREN_WRAP"
      )

      //Method calls section
      buffer ++= Seq(
        "CALL_PARAMETERS_WRAP",
        "ALIGN_MULTILINE_PARAMETERS_IN_CALLS",
        "PREFER_PARAMETERS_WRAP",
        "CALL_PARAMETERS_RPAREN_ON_NEXT_LINE"
      )

      //method call chain
      buffer ++= Seq(
        "METHOD_CALL_CHAIN_WRAP",
        "WRAP_FIRST_METHOD_IN_CALL_CHAIN",
        "ALIGN_MULTILINE_CHAINED_METHODS",
        "KEEP_LINE_BREAKS"
      )

      //brace placement
      buffer ++= Seq(
        "CLASS_BRACE_STYLE",
        "METHOD_BRACE_STYLE",
        "BRACE_STYLE"
      )

      //extends list wrap
      buffer ++= Seq(
        "EXTENDS_LIST_WRAP",
        "EXTENDS_KEYWORD_WRAP"
      )

      //method parameters
      buffer ++= Seq(
        "METHOD_PARAMETERS_WRAP",
        "ALIGN_MULTILINE_PARAMETERS",
        "METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE",
        "METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE"
      )

      //if statement
      buffer ++= Seq(
        "IF_BRACE_FORCE",
        "ELSE_ON_NEW_LINE",
        "SPECIAL_ELSE_IF_TREATMENT"
      )

      //brace forces
      buffer ++= Seq(
        "FOR_BRACE_FORCE",
        "WHILE_BRACE_FORCE",
        "DOWHILE_BRACE_FORCE",
        "WHILE_ON_NEW_LINE",
        "INDENT_CASE_FROM_SWITCH",
        "CATCH_ON_NEW_LINE",
        "FINALLY_ON_NEW_LINE",
        "FOR_STATEMENT_WRAP",
        "ALIGN_MULTILINE_FOR",
        "FOR_STATEMENT_LPAREN_ON_NEXT_LINE",
        "FOR_STATEMENT_RPAREN_ON_NEXT_LINE"
      )

      //modifier list wrap
      buffer ++= Seq("MODIFIER_LIST_WRAP")

      //align in columns
      buffer ++= Seq("ALIGN_GROUP_FIELD_DECLARATIONS")

      buffer ++= Seq("WRAP_LONG_LINES")

      //annotations wrap
      buffer ++= Seq(
        "CLASS_ANNOTATION_WRAP",
        "METHOD_ANNOTATION_WRAP",
        "FIELD_ANNOTATION_WRAP",
        "PARAMETER_ANNOTATION_WRAP",
        "VARIABLE_ANNOTATION_WRAP"
      )

      buffer ++= Seq(
        "KEEP_SIMPLE_BLOCKS_IN_ONE_LINE",
        "KEEP_SIMPLE_METHODS_IN_ONE_LINE",
        "KEEP_FIRST_COLUMN_COMMENT"
      )
    }

    //comments generation
    if (settingsType == SettingsType.COMMENTER_SETTINGS) {
      buffer ++= Seq(
        "LINE_COMMENT_AT_FIRST_COLUMN",
        "BLOCK_COMMENT_AT_FIRST_COLUMN"
      )
    }

    consumer.showStandardOptions(buffer.toArray: _*)

    def opt(fieldName: String, title: String, groupName: String,
            keysAndValues: (Array[String], Array[Int]) = null): Unit = {
      val options = if (keysAndValues != null) Array(keysAndValues._1, keysAndValues._2) else Array()
      consumer.showCustomOption(classOf[ScalaCodeStyleSettings], fieldName, title, groupName, options: _*)
    }

    def opta(fieldName: String, title: String, groupName: String,
             anchor: OptionAnchor, anchorField: String,
             keysAndValues: (Array[String], Array[Int]) = null): Unit = {
      val options = if (keysAndValues != null) Array(keysAndValues._1, keysAndValues._2) else Array()
      consumer.showCustomOption(classOf[ScalaCodeStyleSettings], fieldName, title, groupName, anchor, anchorField, options: _*)
    }

    import ApplicationBundle.message
    import com.intellij.psi.codeStyle.{CodeStyleSettingsCustomizable => GroupNames}

    //Custom options
    if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
      opt("ALIGN_EXTENDS_WITH", message("wrapping.align.when.multiline"), message("wrapping.extends.implements.list"),
        (ScalaCodeStyleSettings.EXTENDS_ALIGN_STRING, ScalaCodeStyleSettings.EXTENDS_ALIGN_VALUES))
      opt("WRAP_BEFORE_WITH_KEYWORD", "Wrap before 'with' keyword", message("wrapping.extends.implements.list"))
      opt("ALIGN_IF_ELSE", "Align if-else statements", message("wrapping.if.statement"))
      opt("METHOD_BRACE_FORCE", "Force braces", METHOD_DEFINITION, BRACE_OPTION_AND_VALUES)
      opt("TRY_BRACE_FORCE", "Force 'try' braces", GroupNames.WRAPPING_TRY_STATEMENT, BRACE_OPTION_AND_VALUES)
      opt("FINALLY_BRACE_FORCE", "Force 'finally' braces", GroupNames.WRAPPING_TRY_STATEMENT, BRACE_OPTION_AND_VALUES)

      opt("CLOSURE_BRACE_FORCE", "Force braces", ANONYMOUS_METHOD, BRACE_OPTION_AND_VALUES)
      opt("PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE", "Parameters on new line", ANONYMOUS_METHOD)

      opt("NOT_CONTINUATION_INDENT_FOR_PARAMS", "Use normal indent for parameters", GroupNames.WRAPPING_METHOD_PARAMETERS)
      opt("ALIGN_TYPES_IN_MULTILINE_DECLARATIONS", "Align parameter types in multiline declarations", GroupNames.WRAPPING_METHOD_PARAMETERS)
      opt("INDENT_FIRST_PARAMETER", "Indent first parameter if on new line", GroupNames.WRAPPING_METHOD_PARAMETERS)
      opt("INDENT_FIRST_PARAMETER_CLAUSE", "Indent first parameter clause if on new line", GroupNames.WRAPPING_METHOD_PARAMETERS)

      consumer.renameStandardOption(GroupNames.WRAPPING_SWITCH_STATEMENT, "'match' statement")
      opt("DO_NOT_INDENT_CASE_CLAUSE_BODY", "Do not indent case clause body", GroupNames.WRAPPING_SWITCH_STATEMENT)
      opt("ALIGN_IN_COLUMNS_CASE_BRANCH", "Align in columns 'case' branches", GroupNames.WRAPPING_SWITCH_STATEMENT)
      opt("ALIGN_COMPOSITE_PATTERN", "Align multiline pattern alternatives", GroupNames.WRAPPING_SWITCH_STATEMENT)
      opt("CASE_CLAUSE_BRACE_FORCE", "Force 'case' branch braces", GroupNames.WRAPPING_SWITCH_STATEMENT, BRACE_OPTION_AND_VALUES)

      opt("PLACE_SELF_TYPE_ON_NEW_LINE", "Place self type on new line", CLASS_DEFINITION)
      opt("KEEP_XML_FORMATTING", "Keep xml formatting", XML_FORMATTING)
      opt("KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST", "Simple one-line lambdas in arg list", GroupNames.WRAPPING_KEEP)

      opt("INDENT_BRACED_FUNCTION_ARGS", "Indent braced arguments", GroupNames.WRAPPING_METHOD_ARGUMENTS_WRAPPING)
      opt("DO_NOT_ALIGN_BLOCK_EXPR_PARAMS", "Do not align block expression parameters", GroupNames.WRAPPING_METHOD_ARGUMENTS_WRAPPING)
      opta("CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN", message("wrapping.new.line.after.lpar"), GroupNames.WRAPPING_METHOD_ARGUMENTS_WRAPPING,
        OptionAnchor.BEFORE, "CALL_PARAMETERS_RPAREN_ON_NEXT_LINE",
        (Array("No new line", "New line always", "New line for multiple arguments"),
          Array(ScalaCodeStyleSettings.NO_NEW_LINE, ScalaCodeStyleSettings.NEW_LINE_ALWAYS, ScalaCodeStyleSettings.NEW_LINE_FOR_MULTIPLE_ARGUMENTS))
      )
      opt("DO_NOT_INDENT_TUPLES_CLOSE_BRACE", "Do not indent tuples closing parenthesis", TUPLES_WRAP)
      opt("ALIGN_TUPLE_ELEMENTS", "Align tuple elements", TUPLES_WRAP)
    }

    if (settingsType == SettingsType.SPACING_SETTINGS) {
      opt("SPACE_AFTER_MODIFIERS_CONSTRUCTOR", "Constructor parameters with modifiers", GroupNames.SPACES_BEFORE_PARENTHESES)
      opt("SPACE_BEFORE_INFIX_METHOD_CALL_PARENTHESES", "Infix method call parentheses", GroupNames.SPACES_BEFORE_PARENTHESES)
      opt("SPACE_BEFORE_INFIX_OPERATOR_LIKE_METHOD_CALL_PARENTHESES", "Infix operator-like method call parentheses", GroupNames.SPACES_BEFORE_PARENTHESES)
      opt("SPACE_BEFORE_INFIX_LIKE_METHOD_PARENTHESES", "Infix method parentheses", GroupNames.SPACES_BEFORE_PARENTHESES)
      opt("PRESERVE_SPACE_AFTER_METHOD_DECLARATION_NAME", "Preserve space before method parentheses", GroupNames.SPACES_BEFORE_PARENTHESES)

      opta("SPACE_BEFORE_BRACE_METHOD_CALL", "Method call left brace", GroupNames.SPACES_BEFORE_LEFT_BRACE,
        GroupNames.OptionAnchor.AFTER, "SPACE_BEFORE_METHOD_LBRACE")

      opt("SPACE_AFTER_TYPE_COLON", "After colon, before declarations' type", GroupNames.SPACES_OTHER)
      opt("SPACE_BEFORE_TYPE_COLON", "Before colon, after declarations' name", GroupNames.SPACES_OTHER)
      opt("SPACE_INSIDE_CLOSURE_BRACES", "Inside closure braces", GroupNames.SPACES_OTHER)
      opt("SPACES_AROUND_AT_IN_PATTERNS", "Around '@' in pattern bindings", GroupNames.SPACES_OTHER)
      opt("NEWLINE_AFTER_ANNOTATIONS", "Newline after annotations", GroupNames.SPACES_OTHER)
      opt("KEEP_COMMENTS_ON_SAME_LINE", "Keep one-line comments on same line", GroupNames.SPACES_OTHER)

      opt("SPACE_BEFORE_TYPE_PARAMETER_IN_DEF_LIST", "Before opening square bracket", GroupNames.SPACES_IN_TYPE_PARAMETERS)

      opt("SPACE_INSIDE_SELF_TYPE_BRACES", "Self type braces", GroupNames.SPACES_WITHIN)
      opt("SPACES_IN_IMPORTS", "Import braces", GroupNames.SPACES_WITHIN)
      opt("SPACES_IN_ONE_LINE_BLOCKS", "Simple one line block braces", GroupNames.SPACES_WITHIN)
    }

    if (settingsType == SettingsType.BLANK_LINES_SETTINGS) {
      opt("BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES", "Around method in inner scopes", GroupNames.BLANK_LINES)
      opt("BLANK_LINES_AROUND_FIELD_IN_INNER_SCOPES", "Around field in inner scopes", GroupNames.BLANK_LINES)
    }

    if (settingsType == SettingsType.LANGUAGE_SPECIFIC) {
      opt("SD_ALIGN_PARAMETERS_COMMENTS", "Align parameter descriptions", ScalaDocFormattingPanel.ALIGNMENT_GROUP)
      opt("SD_ALIGN_EXCEPTION_COMMENTS", "Align throws exception descriptions", ScalaDocFormattingPanel.ALIGNMENT_GROUP)
      opt("SD_ALIGN_RETURN_COMMENTS", "Align return value description", ScalaDocFormattingPanel.ALIGNMENT_GROUP)
      opt("SD_ALIGN_OTHER_TAGS_COMMENTS", "Align other tags descriptions", ScalaDocFormattingPanel.ALIGNMENT_GROUP)

      opt("SD_KEEP_BLANK_LINES_BETWEEN_TAGS", "Keep (blank lines between tags will not be removed)", ScalaDocFormattingPanel.BLANK_LINES_GROUP)
      opt("SD_BLANK_LINE_BEFORE_TAGS", message("checkbox.after.description"), ScalaDocFormattingPanel.BLANK_LINES_GROUP)
      opt("SD_BLANK_LINE_AFTER_PARAMETERS_COMMENTS", message("checkbox.after.parameter.descriptions"), ScalaDocFormattingPanel.BLANK_LINES_GROUP)
      opt("SD_BLANK_LINE_AFTER_RETURN_COMMENTS", message("checkbox.after.return.tag"), ScalaDocFormattingPanel.BLANK_LINES_GROUP)
      opt("SD_BLANK_LINE_BETWEEN_PARAMETERS", "Between parameter descriptions", ScalaDocFormattingPanel.BLANK_LINES_GROUP)
      opt("SD_BLANK_LINE_BEFORE_PARAMETERS", "Before parameter descriptions", ScalaDocFormattingPanel.BLANK_LINES_GROUP)

      opt("SD_PRESERVE_SPACES_IN_TAGS", "Preserve spaces in tags", ScalaDocFormattingPanel.OTHER_GROUP)
    }

  }

  //custom groups
  private val METHOD_DEFINITION = "Method definition"
  private val ANONYMOUS_METHOD = "Anonymous method definition"
  private val CLASS_DEFINITION = "Class definition"
  private val XML_FORMATTING = "Xml formatting"
  private val TUPLES_WRAP = "Tuple"

  override def getDefaultCommonSettings: CommonCodeStyleSettings = {
    val commonSettings = new CommonCodeStyleSettings(getLanguage)
    val indentOptions = commonSettings.initIndentOptions
    indentOptions.INDENT_SIZE = 2
    indentOptions.TAB_SIZE = 2
    indentOptions.CONTINUATION_INDENT_SIZE = 2
    commonSettings.KEEP_FIRST_COLUMN_COMMENT = false //added here to comply with prior default behavior
    commonSettings
  }

  override def getDisplayPriority = DisplayPriority.COMMON_SETTINGS

  override def getIndentOptionsEditor = new SmartIndentOptionsEditor
}

object ScalaLanguageCodeStyleSettingsProvider {
  private val BRACE_OPTION_AND_VALUES: (Array[String], Array[Int]) = (
    CodeStyleSettingsCustomizable.BRACE_OPTIONS,
    CodeStyleSettingsCustomizable.BRACE_VALUES
  )


  private val IndentsCodeSample =
    """class A {
      |  def foo[A](x: Int,
      |             y: Int): Int = 42
      |
      |  foo[Int](1, 2)
      |
      |  def bar(): A = this
      |
      |  bar()
      |    .bar()
      |    .bar()
      |}
    """.stripMargin.withNormalizedSeparator

  private val GeneralCodeSample = IndentsCodeSample

  private val SpacingCodeSample: String =
    """import scala.collection.immutable.{Seq, List} // one line comment 1
      |
      |class A {
      |
      |  def foo[A](): Int = 42
      |
      |  // `Within | Simple one line block braces` changes can only be visible if
      |  // `Wrapping and Braces | Keep when reformatting | Simple blocks/methods in one line` setting is enabled
      |  def bar(x: Int): Option[Int] = { Some(24) }
      |
      |  class B() { // one line comment 2
      |  }
      |
      |  class C private(val param1: Int, param2: Long) {
      |  }
      |
      |  foo[Int]()
      |
      |  bar(7)
      |
      |  new B()
      |
      |  List(1, 2, 3).foreach { item =>
      |  }
      |
      |  // `Within | Self type braces` changes can only be visible if
      |  // `Wrapping and Braces | Class definition | Place self type on new line` setting is disabled
      |  trait T { self: A =>
      |  }
      |
      |  if (true) { // one line comment 3
      |  }
      |  else if (false) {
      |  }
      |  else {
      |  }
      |
      |  for (x <- Option(1); y <- Option(2))
      |    yield x * y
      |
      |  for {
      |    item <- Seq("item1", "item2", "item3")
      |  } yield item
      |
      |  while (true) {
      |  }
      |
      |  do {
      |  } while (true)
      |
      |  try {
      |  } catch {
      |  } finally {
      |  }
      |
      |  obj.method()
      |  obj.method("1")
      |  obj.method("1", "2")
      |  obj method()
      |  obj method("1")  // operator-like method call, takes single argument
      |  obj method("1", "2")
      |  obj * 42 // operator calls are always surrounded with space
      |  obj % (42)
      |  obj + (42, 23)
      |
      |  "hello" slice(1, 2) intern()
      |
      |  def +++(s: StringBuilder): StringBuilder = {
      |    s append (this.toString)
      |  }
      |
      |  def fooWithExplicitSpace () {}
      |
      |  bar(7) match {
      |    case s@Some(24) =>
      |    case _ =>
      |  }
      |
      |  @inline def multiply(x: Int, y: Int): Int = x * y
      |
      |}
    """.stripMargin.withNormalizedSeparator

  private val WrappingAndBracesSample =
    """class A {
      |  def foo1 = 42
      |
      |  def foo2: Unit = { println(42) }
      |
      |  def foo3(
      |    x: Int,
      |    y: Long,
      |    str: String
      |  ) = 42
      |
      |  def foo4(x: Int,
      |           y: Long,
      |           str: String) = 42
      |
      |  def foo5
      |  (x: Int, y: Int)
      |  (u: Int, v: Int) = 42
      |
      |  class Foo(p1: Int = 0,
      |            param2: String = "1") {
      |    def foo(x: Int = 0, y: Int = 1, z: Int = 2) =
      |      new Foo
      |  }
      |
      |  class Foo2(
      |    p1: Int = 0,
      |    param2: String = "1"
      |  ){}
      |
      |  val fooObj = new Foo
      |
      |  fooObj.foo().foo(1, 2).foo(z = 1, y = 2).foo().foo(1, 2, 3).foo()
      |    .foo().foo()
      |    .foo(1, 2, 3).foo()
      |
      |  fooObj.foo().foo(1, 2)
      |
      |  multipleParams(param2 = 4,
      |                 param3 = 5) {
      |    println("foo")
      |  }
      |
      |  Seq(1, 2).map(x => 42)
      |
      |  Seq(1, 2).map { x => 42 }
      |
      |  Seq(1, 2).map(x =>
      |    42
      |  )
      |
      |  Seq(1, 2).map(x => {
      |    42
      |  })
      |
      |  Seq(1, 2).map { x =>
      |    42
      |  }
      |
      |  val b1 = if (true) 1 else 2
      |
      |  val b2 = if (true)
      |             1
      |           else {
      |           }
      |
      |  if (true) {
      |    42
      |  } else if (false) {
      |    23
      |  } else 423
      |
      |  for (i <- 1 to 5) yield i + 1
      |
      |  while (true)
      |    println(42)
      |
      |  while (true) println(23)
      |
      |  do println(42) while(true)
      |
      |  do println(42)
      |  while(true)
      |
      |  Some(3) match {
      |    case Some(a) if a != 2 => println("1")
      |    case Some(1) |
      |       Some(2) => println("2")
      |
      |    case _ => println("3")
      |  }
      |
      |  try fetchUser() finally println("finally")
      |
      |  try fetchUser() catch {
      |    case _: UserNotFoundException =>
      |      DefaultUser
      |  } finally {
      |    println("finally")
      |  }
      |
      |  try a + 2
      |  catch {
      |    case e => (i: Int) => i + 1
      |  } finally
      |    doNothing
      |
      |  val infixExpr1 = 1 + 2 + (3 + 4) + 5 + 6 +
      |    7 + 8 + 9 + 10 + 11 + 12 + 13 + (14 +
      |    15) + 16 + 17 * 18 + 19 + 20
      |
      |  { println(42) }
      |
      |//comment at first line
      | //comment at second line
      |
      |  trait A { self: B =>
      |
      |  }
      |
      |  val tuple = (1, 2, 3, 4)
      |
      |  val tupleMultiline = (
      |    1, 2,
      |    3, 4
      |  )
      |}
      |
      |class C1 extends B with T1 with T2 {
      |}
      |
      |class C2 extends B with T1 {
      |}
      |
      |@Anno1 @Anno2
      |class Foo3()
      |
      |@Annotation1 @Annotation2(param1 = "value1", param2 = "value2")
      |class Foo4(
      | @Annotation1 @Annotation2(param1 = "value1", param2 = "value2")
      |  x: Int,
      |  @A1 @A2
      |  y: Int
      |) {
      |  @Annotation1 @Annotation2(param1 = "value1", param2 = "value2")
      |  val field = 42
      |
      |  @Anno1 @Anno2
      |  def foo1(): Unit = {
      |  }
      |
      |  @Annotation1 @Annotation2(param1 = "value1", param2 = "value2")
      |  def foo2(): Unit = {
      |   @Annotation1 @Annotation2(param1 = "value1", param2 = "value2")
      |   val localVal1: Int = 0
      |   @A1 @A2
      |   val localVal2: Int = 0
      |  }
      |}
      |
      |class XmlExamples {
      |  val books =
      |    <books>
      |      <book id="b1615">Don Quixote</book>
      |      <book id="b1867">War and Peace</book>
      |    </books>
      |
      |  tag match {
      |    case <book id="42">That book</book>  =>
      |  println ("found!")
      |    case _ =>
      |  }
      |}
      |""".stripMargin.withNormalizedSeparator

  private val BlankLinesCodeSample =
    """//code
      |package A
      |
      |
      |import a.b
      |
      |import b.c
      |import c.d
      |
      |
      |class A {
      |  def foo = 1
      |  def goo = 2
      |  type S = String
      |  val a = 1
      |
      |  val b = 2
      |  val c = 2
      |
      |
      |}
      |
      |trait B {
      |
      |  val b = 2
      |  val c = 2
      |
      |  def foo
      |  def goo
      |  def too = {
      |
      |
      |    val x = 2
      |    new J {
      |      def goo = 1
      |    }
      |    def tooInner = 42
      |  }
      |
      |  def baz = {
      |    42
      |  }
      |}""".stripMargin.withNormalizedSeparator
}
