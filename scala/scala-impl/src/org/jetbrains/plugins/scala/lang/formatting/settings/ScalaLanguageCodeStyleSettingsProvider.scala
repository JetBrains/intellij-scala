package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.application.options.{CodeStyleAbstractConfigurable, CodeStyleAbstractPanel, SmartIndentOptionsEditor}
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable.OptionAnchor
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType
import com.intellij.psi.codeStyle._
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaLanguageCodeStyleSettingsProvider._
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaLanguage}

import scala.collection.mutable.ArrayBuffer

class ScalaLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {

  override def createConfigurable(baseSettings: CodeStyleSettings, modelSettings: CodeStyleSettings): CodeStyleConfigurable =
    new ScalaCodeStyleAbstractConfigurable(baseSettings, modelSettings)

  override def getConfigurableDisplayName: String = ScalaBundle.message("options.scala.display.name")

  override def getDisplayPriority = DisplayPriority.COMMON_SETTINGS

  override def getLanguage: Language = ScalaLanguage.INSTANCE

  override def getIndentOptionsEditor = new SmartIndentOptionsEditor

  override def createCustomSettings(settings: CodeStyleSettings) = new ScalaCodeStyleSettings(settings)

  override def getCodeSample(settingsType: SettingsType): String =
    settingsType match {
      case SettingsType.INDENT_SETTINGS              => IndentsCodeSample
      case SettingsType.SPACING_SETTINGS             => SpacingCodeSample
      case SettingsType.WRAPPING_AND_BRACES_SETTINGS => WrappingAndBracesSample
      case SettingsType.BLANK_LINES_SETTINGS         => BlankLinesCodeSample
      case SettingsType.LANGUAGE_SPECIFIC            => GeneralCodeSample // TODO: looks like other setting types are not displayed for now
      case _                                         => GeneralCodeSample
    }

  override def customizeDefaults(commonSettings: CommonCodeStyleSettings, indentOptions: CommonCodeStyleSettings.IndentOptions): Unit = {
    super.customizeDefaults(commonSettings, indentOptions)
    indentOptions.INDENT_SIZE = 2
    indentOptions.TAB_SIZE = 2
    indentOptions.CONTINUATION_INDENT_SIZE = 2
    commonSettings.KEEP_FIRST_COLUMN_COMMENT = false //added here to comply with prior default behavior
  }

  override def customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType): Unit = {
    val settingsToEnable: ArrayBuffer[String] = new ArrayBuffer

    def enableSettings(@NonNls fieldNames: String*): Unit = settingsToEnable ++= fieldNames

    //spacing
    if (settingsType == SettingsType.SPACING_SETTINGS) {
      consumer.renameStandardOption("SPACE_BEFORE_TYPE_PARAMETER_LIST", ScalaBundle.message("spaces.panel.before.opening.square.bracket"))

      enableSettings(
        // After
        "SPACE_AFTER_COMMA",
        "SPACE_AFTER_SEMICOLON",

        // Before
        "SPACE_BEFORE_IF_PARENTHESES",
        "SPACE_BEFORE_FOR_PARENTHESES",
        "SPACE_BEFORE_WHILE_PARENTHESES",
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
      enableSettings(
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
      consumer.renameStandardOption("BINARY_OPERATION_WRAP", ScalaBundle.message("wrapping.and.braces.panel.renamed.infix.expressions"))
      consumer.renameStandardOption("EXTENDS_LIST_WRAP"    , ScalaBundle.message("wrapping.and.braces.panel.renamed.extends.with.list"))
      consumer.renameStandardOption("EXTENDS_KEYWORD_WRAP" , ScalaBundle.message("wrapping.and.braces.panel.renamed.extends.keyword"))
      consumer.renameStandardOption("FOR_BRACE_FORCE"      , ScalaBundle.message("wrapping.and.braces.panel.renamed.force.yield.braces"))

      //Binary expression section
      enableSettings(
        "BINARY_OPERATION_WRAP",
        "ALIGN_MULTILINE_BINARY_OPERATION",
        "ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION",
        "PARENTHESES_EXPRESSION_LPAREN_WRAP",
        "PARENTHESES_EXPRESSION_RPAREN_WRAP"
      )

      //Method calls section
      enableSettings(
        "CALL_PARAMETERS_WRAP",
        "ALIGN_MULTILINE_PARAMETERS_IN_CALLS",
        "PREFER_PARAMETERS_WRAP",
        "CALL_PARAMETERS_RPAREN_ON_NEXT_LINE"
      )

      //method call chain
      enableSettings(
        "METHOD_CALL_CHAIN_WRAP",
        "WRAP_FIRST_METHOD_IN_CALL_CHAIN",
        "ALIGN_MULTILINE_CHAINED_METHODS",
        "KEEP_LINE_BREAKS"
      )

      //brace placement
      enableSettings(
        "CLASS_BRACE_STYLE",
        "METHOD_BRACE_STYLE",
        "BRACE_STYLE"
      )

      //extends list wrap
      enableSettings(
        "EXTENDS_LIST_WRAP",
        "EXTENDS_KEYWORD_WRAP"
      )

      //method parameters
      enableSettings(
        "METHOD_PARAMETERS_WRAP",
        "ALIGN_MULTILINE_PARAMETERS",
        "METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE",
        "METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE"
      )

      //if statement
      enableSettings(
        "IF_BRACE_FORCE",
        "ELSE_ON_NEW_LINE",
        "SPECIAL_ELSE_IF_TREATMENT"
      )

      //brace forces
      enableSettings(
        "FOR_BRACE_FORCE",
        "WHILE_BRACE_FORCE",
        "DOWHILE_BRACE_FORCE",
        "WHILE_ON_NEW_LINE",
        "INDENT_CASE_FROM_SWITCH",
        "CATCH_ON_NEW_LINE",
        "FINALLY_ON_NEW_LINE",
        "FOR_STATEMENT_WRAP",
        "ALIGN_MULTILINE_FOR"
      )

      //modifier list wrap
      enableSettings("MODIFIER_LIST_WRAP")

      //align in columns
      enableSettings("ALIGN_GROUP_FIELD_DECLARATIONS")

      enableSettings("WRAP_LONG_LINES")

      //annotations wrap
      enableSettings(
        "CLASS_ANNOTATION_WRAP",
        "METHOD_ANNOTATION_WRAP",
        "FIELD_ANNOTATION_WRAP",
        "PARAMETER_ANNOTATION_WRAP",
        "VARIABLE_ANNOTATION_WRAP"
      )

      enableSettings(
        "KEEP_SIMPLE_BLOCKS_IN_ONE_LINE",
        "KEEP_SIMPLE_METHODS_IN_ONE_LINE",
        "KEEP_FIRST_COLUMN_COMMENT"
      )
    }

    //comments generation
    if (settingsType == SettingsType.COMMENTER_SETTINGS) {
      enableSettings(
        "LINE_COMMENT_AT_FIRST_COLUMN",
        "BLOCK_COMMENT_AT_FIRST_COLUMN"
      )
    }

    consumer.showStandardOptions(settingsToEnable.toArray: _*)

    def opt(@NonNls fieldName: String, title: String, groupName: String,
            keysAndValues: (Array[String], Array[Int]) = null): Unit = {
      val options = if (keysAndValues != null) Array(keysAndValues._1, keysAndValues._2) else Array()
      consumer.showCustomOption(classOf[ScalaCodeStyleSettings], fieldName, title, groupName, options: _*)
    }

    //noinspection SpellCheckingInspection
    def opta(@NonNls fieldName: String, title: String, groupName: String,
             anchor: OptionAnchor, @NonNls anchorField: String,
             keysAndValues: Array[(String, Int)] = Array()): Unit = {
      val options: Array[AnyRef] = if (keysAndValues.nonEmpty) {
        Array(keysAndValues.map(_._1), keysAndValues.map(_._2))
      } else {
        Array()
      }
      consumer.showCustomOption(classOf[ScalaCodeStyleSettings], fieldName, title, groupName, anchor, anchorField, options: _*)
    }

    import ApplicationBundle.{message => appMessage}
    import com.intellij.psi.codeStyle.{CodeStyleSettingsCustomizable => GroupNames}

    //Custom options
    if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
      import WrappingAndBracesCustomGroupNames._

      val ExtendsWithList = appMessage("wrapping.extends.implements.list")
      opt("ALIGN_EXTENDS_WITH", appMessage("wrapping.align.when.multiline"), ExtendsWithList,
        (ScalaCodeStyleSettings.EXTENDS_ALIGN_STRING, ScalaCodeStyleSettings.EXTENDS_ALIGN_VALUES))
      opt("WRAP_BEFORE_WITH_KEYWORD", ScalaBundle.message("wrapping.and.braces.panel.wrap.before.with.keyword"), ExtendsWithList)
      opt("ALIGN_IF_ELSE", ScalaBundle.message("wrapping.and.braces.panel.align.if.else.statements"), appMessage("wrapping.if.statement"))
      opt("METHOD_BRACE_FORCE", ScalaBundle.message("wrapping.and.braces.panel.force.braces"), METHOD_DEFINITION, BRACE_OPTION_AND_VALUES)
      opt("TRY_BRACE_FORCE", ScalaBundle.message("wrapping.and.braces.panel.force.try.braces"), GroupNames.WRAPPING_TRY_STATEMENT, BRACE_OPTION_AND_VALUES)
      opt("FINALLY_BRACE_FORCE", ScalaBundle.message("wrapping.and.braces.panel.force.finally.braces"), GroupNames.WRAPPING_TRY_STATEMENT, BRACE_OPTION_AND_VALUES)

      opt("CLOSURE_BRACE_FORCE", ScalaBundle.message("wrapping.and.braces.panel.force.braces"), ANONYMOUS_METHOD, BRACE_OPTION_AND_VALUES)
      opt("PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE", ScalaBundle.message("wrapping.and.braces.panel.parameters.on.new.line"), ANONYMOUS_METHOD)

      opt("NOT_CONTINUATION_INDENT_FOR_PARAMS", ScalaBundle.message("wrapping.and.braces.panel.use.normal.indent.for.parameters"), GroupNames.WRAPPING_METHOD_PARAMETERS)
      opt("ALIGN_TYPES_IN_MULTILINE_DECLARATIONS", ScalaBundle.message("wrapping.and.braces.panel.align.parameter.types.in.multiline.declarations"), GroupNames.WRAPPING_METHOD_PARAMETERS)
      opt("INDENT_FIRST_PARAMETER", ScalaBundle.message("wrapping.and.braces.panel.indent.first.parameter.if.on.new.line"), GroupNames.WRAPPING_METHOD_PARAMETERS)
      opt("INDENT_FIRST_PARAMETER_CLAUSE", ScalaBundle.message("wrapping.and.braces.panel.indent.first.parameter.clause.if.on.new.line"), GroupNames.WRAPPING_METHOD_PARAMETERS)

      consumer.renameStandardOption(GroupNames.WRAPPING_SWITCH_STATEMENT, ScalaBundle.message("wrapping.and.braces.panel.match.statement"))
      opt("DO_NOT_INDENT_CASE_CLAUSE_BODY", ScalaBundle.message("wrapping.and.braces.panel.do.not.indent.case.clause.body"), GroupNames.WRAPPING_SWITCH_STATEMENT)
      opt("ALIGN_IN_COLUMNS_CASE_BRANCH", ScalaBundle.message("wrapping.and.braces.panel.align.in.columns.case.branches"), GroupNames.WRAPPING_SWITCH_STATEMENT)
      opt("ALIGN_COMPOSITE_PATTERN", ScalaBundle.message("wrapping.and.braces.panel.align.multiline.pattern.alternatives"), GroupNames.WRAPPING_SWITCH_STATEMENT)
      opt("CASE_CLAUSE_BRACE_FORCE", ScalaBundle.message("wrapping.and.braces.panel.force.case.branch.braces"), GroupNames.WRAPPING_SWITCH_STATEMENT, BRACE_OPTION_AND_VALUES)

      opt("PLACE_SELF_TYPE_ON_NEW_LINE", ScalaBundle.message("wrapping.and.braces.panel.place.self.type.on.new.line"), CLASS_DEFINITION)
      opt("KEEP_XML_FORMATTING", ScalaBundle.message("wrapping.and.braces.panel.keep.xml.formatting"), XML_FORMATTING)
      opt("KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST", ScalaBundle.message("wrapping.and.braces.panel.simple.one.line.lambdas.in.arg.list"), GroupNames.WRAPPING_KEEP)

      opt("INDENT_BRACED_FUNCTION_ARGS", ScalaBundle.message("wrapping.and.braces.panel.indent.braced.arguments"), GroupNames.WRAPPING_METHOD_ARGUMENTS_WRAPPING)
      opt("DO_NOT_ALIGN_BLOCK_EXPR_PARAMS", ScalaBundle.message("wrapping.and.braces.panel.do.not.align.block.expression.parameters"), GroupNames.WRAPPING_METHOD_ARGUMENTS_WRAPPING)

      val newLinesOptions: Array[(String, Int)] = {
        import ScalaCodeStyleSettings._
        Array(
          ScalaBundle.message("wrapping.and.braces.panel.new.line.options.no.new.line") -> NO_NEW_LINE,
          ScalaBundle.message("wrapping.and.braces.panel.new.line.options.new.line.always") -> NEW_LINE_ALWAYS,
          ScalaBundle.message("wrapping.and.braces.panel.new.line.options.new.line.for.multiple.arguments") -> NEW_LINE_FOR_MULTIPLE_ARGUMENTS
        )
      }
      opta("CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN", appMessage("wrapping.new.line.after.lpar"), GroupNames.WRAPPING_METHOD_ARGUMENTS_WRAPPING,
        OptionAnchor.BEFORE, "CALL_PARAMETERS_RPAREN_ON_NEXT_LINE", newLinesOptions
      )
      opt("DO_NOT_INDENT_TUPLES_CLOSE_BRACE", ScalaBundle.message("wrapping.and.braces.panel.do.not.indent.tuples.closing.parenthesis"), TUPLES_WRAP)
      opt("ALIGN_TUPLE_ELEMENTS", ScalaBundle.message("wrapping.and.braces.panel.align.tuple.elements"), TUPLES_WRAP)

      opt("INDENT_TYPE_ARGUMENTS", ScalaBundle.message("wrapping.and.braces.panel.indent"), TYPE_ARGUMENTS)
      opt("INDENT_TYPE_PARAMETERS", ScalaBundle.message("wrapping.and.braces.panel.indent"), TYPE_PARAMETERS)
    }

    if (settingsType == SettingsType.SPACING_SETTINGS) {
      opt("SPACE_AFTER_MODIFIERS_CONSTRUCTOR", ScalaBundle.message("spaces.panel.constructor.parameters.with.modifiers"), GroupNames.SPACES_BEFORE_PARENTHESES)
      opt("SPACE_BEFORE_INFIX_METHOD_CALL_PARENTHESES", ScalaBundle.message("spaces.panel.infix.method.call.parentheses"), GroupNames.SPACES_BEFORE_PARENTHESES)
      opt("SPACE_BEFORE_INFIX_OPERATOR_LIKE_METHOD_CALL_PARENTHESES", ScalaBundle.message("spaces.panel.infix.operator.like.method.call.parentheses"), GroupNames.SPACES_BEFORE_PARENTHESES)
      opt("SPACE_BEFORE_INFIX_LIKE_METHOD_PARENTHESES", ScalaBundle.message("spaces.panel.infix.method.parentheses"), GroupNames.SPACES_BEFORE_PARENTHESES)
      opt("PRESERVE_SPACE_AFTER_METHOD_DECLARATION_NAME", ScalaBundle.message("spaces.panel.preserve.space.before.method.parentheses"), GroupNames.SPACES_BEFORE_PARENTHESES)

      opta("SPACE_BEFORE_BRACE_METHOD_CALL", ScalaBundle.message("spaces.panel.method.call.left.brace"), GroupNames.SPACES_BEFORE_LEFT_BRACE,
        GroupNames.OptionAnchor.AFTER, "SPACE_BEFORE_METHOD_LBRACE")

      opt("SPACE_AFTER_TYPE_COLON", ScalaBundle.message("spaces.panel.after.colon.before.declarations.type"), GroupNames.SPACES_OTHER)
      opt("SPACE_BEFORE_TYPE_COLON", ScalaBundle.message("spaces.panel.before.colon.after.declarations.name"), GroupNames.SPACES_OTHER)
      opt("SPACE_INSIDE_CLOSURE_BRACES", ScalaBundle.message("spaces.panel.inside.closure.braces"), GroupNames.SPACES_OTHER)
      opt("SPACES_AROUND_AT_IN_PATTERNS", ScalaBundle.message("spaces.panel.around.at.in.pattern.bindings"), GroupNames.SPACES_OTHER)
      opt("NEWLINE_AFTER_ANNOTATIONS", ScalaBundle.message("spaces.panel.newline.after.annotations"), GroupNames.SPACES_OTHER)
      opt("KEEP_COMMENTS_ON_SAME_LINE", ScalaBundle.message("spaces.panel.keep.one.line.comments.on.same.line"), GroupNames.SPACES_OTHER)

      opt("SPACE_BEFORE_TYPE_PARAMETER_IN_DEF_LIST", ScalaBundle.message("spaces.panel.before.opening.square.bracket"), GroupNames.SPACES_IN_TYPE_PARAMETERS)

      opt("SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON", ScalaBundle.message("spaces.panel.before.context.bound.colon.leading"), GroupNames.SPACES_IN_TYPE_PARAMETERS)
      opt("SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON_HK", ScalaBundle.message("spaces.panel.before.context.bound.colon.leading.higher.kinded"), GroupNames.SPACES_IN_TYPE_PARAMETERS)
      opt("SPACE_BEFORE_TYPE_PARAMETER_REST_CONTEXT_BOUND_COLONS", ScalaBundle.message("spaces.panel.before.context.bound.colon.rest"), GroupNames.SPACES_IN_TYPE_PARAMETERS)

      opt("SPACE_INSIDE_SELF_TYPE_BRACES", ScalaBundle.message("spaces.panel.self.type.braces"), GroupNames.SPACES_WITHIN)
      opt("SPACES_IN_IMPORTS", ScalaBundle.message("spaces.panel.import.braces"), GroupNames.SPACES_WITHIN)
      opt("SPACES_IN_ONE_LINE_BLOCKS", ScalaBundle.message("spaces.panel.simple.one.line.block.braces"), GroupNames.SPACES_WITHIN)
    }

    if (settingsType == SettingsType.BLANK_LINES_SETTINGS) {
      opt("BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES", ScalaBundle.message("blank.lines.panel.around.method.in.inner.scopes"), GroupNames.BLANK_LINES)
      opt("BLANK_LINES_AROUND_FIELD_IN_INNER_SCOPES",  ScalaBundle.message("blank.lines.panel.around.field.in.inner.scopes"), GroupNames.BLANK_LINES)
    }

    if (settingsType == SettingsType.LANGUAGE_SPECIFIC) {
      opt("SD_ALIGN_PARAMETERS_COMMENTS", ScalaBundle.message("scaladoc.panel.align.parameter.descriptions"), ScalaDocFormattingPanel.ALIGNMENT_GROUP)
      opt("SD_ALIGN_EXCEPTION_COMMENTS", ScalaBundle.message("scaladoc.panel.align.throws.exception.descriptions"), ScalaDocFormattingPanel.ALIGNMENT_GROUP)
      opt("SD_ALIGN_RETURN_COMMENTS", ScalaBundle.message("scaladoc.panel.align.return.value.description"), ScalaDocFormattingPanel.ALIGNMENT_GROUP)
      opt("SD_ALIGN_OTHER_TAGS_COMMENTS", ScalaBundle.message("scaladoc.panel.align.other.tags.descriptions"), ScalaDocFormattingPanel.ALIGNMENT_GROUP)

      opt("SD_KEEP_BLANK_LINES_BETWEEN_TAGS", ScalaBundle.message("scaladoc.panel.blank.lines.keep"), ScalaDocFormattingPanel.BLANK_LINES_GROUP)
      opt("SD_BLANK_LINE_BEFORE_TAGS", appMessage("checkbox.after.description"), ScalaDocFormattingPanel.BLANK_LINES_GROUP)
      opt("SD_BLANK_LINE_AFTER_PARAMETERS_COMMENTS", appMessage("checkbox.after.parameter.descriptions"), ScalaDocFormattingPanel.BLANK_LINES_GROUP)
      opt("SD_BLANK_LINE_AFTER_RETURN_COMMENTS", appMessage("checkbox.after.return.tag"), ScalaDocFormattingPanel.BLANK_LINES_GROUP)
      opt("SD_BLANK_LINE_BETWEEN_PARAMETERS", ScalaBundle.message("scaladoc.panel.between.parameter.descriptions"), ScalaDocFormattingPanel.BLANK_LINES_GROUP)
      opt("SD_BLANK_LINE_BEFORE_PARAMETERS", ScalaBundle.message("scaladoc.panel.before.parameter.descriptions"), ScalaDocFormattingPanel.BLANK_LINES_GROUP)

      opt("SD_PRESERVE_SPACES_IN_TAGS", ScalaBundle.message("scaladoc.panel.preserve.spaces.in.tags"), ScalaDocFormattingPanel.OTHER_GROUP)
    }
  }
}

object ScalaLanguageCodeStyleSettingsProvider {

  //noinspection TypeAnnotation
  private object WrappingAndBracesCustomGroupNames {
    val METHOD_DEFINITION = ScalaBundle.message("wrapping.and.braces.panel.groups.method.definition")
    val ANONYMOUS_METHOD = ScalaBundle.message("wrapping.and.braces.panel.groups.anonymous.method.definition")
    val CLASS_DEFINITION = ScalaBundle.message("wrapping.and.braces.panel.groups.class.definition")
    val XML_FORMATTING = ScalaBundle.message("wrapping.and.braces.panel.groups.xml.formatting")
    val TUPLES_WRAP = ScalaBundle.message("wrapping.and.braces.panel.groups.tuple")
    val TYPE_ARGUMENTS = ScalaBundle.message("wrapping.and.braces.panel.groups.type.arguments")
    val TYPE_PARAMETERS = ScalaBundle.message("wrapping.and.braces.panel.groups.type.parameters")
  }

  private val Log = Logger.getInstance(getClass)

  private class ScalaCodeStyleAbstractConfigurable(settings: CodeStyleSettings, cloneSettings: CodeStyleSettings)
    extends CodeStyleAbstractConfigurable(settings, cloneSettings, "Scala") {

    override protected def createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel = {
      panel = try new ScalaTabbedCodeStylePanel(getCurrentSettings, settings) catch {
        case ex: Throwable =>
          Log.error("Error occurred during scala code style panel initialization", ex)
          throw ex
      }
      panel
    }

    override def setModel(model: CodeStyleSchemesModel): Unit = {
      super.setModel(model)
      panel.onProjectSet(model.getProject)
      panel.onModelSet(model)
    }

    private var panel: ScalaTabbedCodeStylePanel = _
  }

  private val BRACE_OPTION_AND_VALUES: (Array[String], Array[Int]) = (
    CodeStyleSettingsCustomizable.BRACE_OPTIONS,
    CodeStyleSettingsCustomizable.BRACE_VALUES
  )

  private def example(@NonNls str: String): String = str.stripMargin.withNormalizedSeparator

  private val IndentsCodeSample = example {
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
    """
  }

  private val GeneralCodeSample = IndentsCodeSample

  private val SpacingCodeSample: String = example {
    """import scala.collection.immutable.{Seq, List} // one line comment 1
      |
      |class A {
      |
      |  def foo(): Int = 42
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
      |  trait X1[F]
      |  trait X2[F: T]
      |  trait X4[F: T1 : T2]
      |  trait X3[F[_] : T]
      |  trait X5[F[_] : T1 : T2]
      |  trait X6[F <: G : T1 : T2]
      |}
    """
  }

  private val WrappingAndBracesSample =example {
    """private final class A {
      |  def foo1 = 42
      |
      |  private final def foo2: Unit = { println(42) }
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
      |  val x = 42
      |  val yyy = 23
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
      |  for (x <- 1 to 5) yield x + 1
      |
      |  for (x <- 1 to 5;
      |       y <- 5 to 10) yield x * y
      |
      |  for {x <- 1 to 5
      |       y <- 5 to 10} yield x * y
      |
      |  for {
      |    x <- 1 to 5
      |    y <- 5 to 10
      |  } yield x * y
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
      |       Some(2) => println("1 or 2")
      |    case Some(3) => println("3")
      |
      |    case _ => println("other")
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
      |
      |  def foo[
      |    A, B,
      |    C <: T[
      |      D, E,
      |      F
      |    ]
      |  ]: Unit = ???
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
      |"""
  }

  private val BlankLinesCodeSample = example {
    """//comment
      |package A
      |
      |//comment
      |import a.b
      |
      |import b.c
      |import c.d
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
      |}
      |"""
  }
}
