package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

/*
 Complex interactions between different type mismatch highlighting features, including:
   https://youtrack.jetbrains.net/issue/SCL-15138 Only highlight initial, not derivative errors
   https://youtrack.jetbrains.net/issue/SCL-14778 Better highlighting of compound expressions
   https://youtrack.jetbrains.net/issue/SCL-14777 Block expression: underline final expression instead of closing brace
   https://youtrack.jetbrains.net/issue/SCL-15250 Use inlay type ascription to indicate type mismatch
   https://youtrack.jetbrains.net/issue/SCL-15481 Type mismatch: fine-grained diff
   https://youtrack.jetbrains.net/issue/SCL-15544 Type ascription: highlight type, not expression
   https://youtrack.jetbrains.net/issue/SCL-15571 Type mismatch errors: widen literal types when the value is of no importance
   https://youtrack.jetbrains.net/issue/SCL-15592 Method / constructor invocation: highlight only a single kind of error
   https://youtrack.jetbrains.net/issue/SCL-15594 Don't highlight arguments when there are multiple inapplicable overloaded methods
   https://youtrack.jetbrains.net/issue/SCL-15588 Method / constructor invocation: highlight inapplicable methods as inapplicable
   https://youtrack.jetbrains.net/issue/SCL-14042 Show missing arguments as inlay hints
   https://youtrack.jetbrains.net/issue/IDEA-195336 Show missing arguments as inlay hints
   https://youtrack.jetbrains.net/issue/SCL-15754 Don't highlight a term before a dot as a "type mismatch"
   https://youtrack.jetbrains.com/issue/SCL-15773 Method / constructor invocation: highlight "space" before a closing paren on missing argument(s)
   https://youtrack.jetbrains.com/issue/SCL-15783 Method / constructor invocation: highlight comma on excessive argument(s)
 */

abstract class TypeMismatchHighlightingTestBase extends ScalaHighlightingTestBase {
  override protected def withHints = true

  // TODO Remove when / if type mismatch hints will be enabled by default, SCL-15250
  private var savedIsTypeMismatchHints: Boolean = _

  override protected def setUp(): Unit = {
    super.setUp()
    savedIsTypeMismatchHints = ScalaProjectSettings.in(getProject).isTypeMismatchHints
    ScalaProjectSettings.in(getProject).setTypeMismatchHints(true)
  }

  override def tearDown(): Unit = {
    ScalaProjectSettings.in(getProject).setTypeMismatchHints(savedIsTypeMismatchHints)
    super.tearDown()
  }
}


class TypeMismatchHighlightingTest extends TypeMismatchHighlightingTestBase {

  // Type ascription, SCL-15544

  // SCL-15544
  def testTypeAscriptionOk(): Unit = {
    assertMessages(errorsFromScalaCode("1: Int"))()
  }

  // Highlight type ascription differently from type mismatch (handled in ScTypedExpressionAnnotator), SCL-15544
  def testTypeAscriptionError(): Unit = {
    assertMessages(errorsFromScalaCode("(): Int"))(Error("Int", "Cannot upcast Unit to Int"))
  }

  // Widen literal type when non-literal type is ascribed (handled in ScTypedExpressionAnnotator), SCL-15571
  def testTypeAscriptionErrorWiden(): Unit = {
    assertMessages(errorsFromScalaCode("true: Int"))(Error("Int", "Cannot upcast Boolean to Int"))
  }

  // Fine-grained type ascription diff, SCL-15544, SCL-15481
  def testTypeAscriptionErrorDiff(): Unit = {
    assertMessages(errorsFromScalaCode("Some(1): Option[String]"))(Error("String", "Cannot upcast Some[Int] to Option[String]"))
  }

  // Expected type & type ascription, SCL-15544

  // SCL-15544
  def testTypeMismatchAndTypeAscriptionOk(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int = 1: Int"))()
  }

  // When present, highlight type ascription, not expression, SCL-15544
  def testTypeMismatchAndTypeAscriptionError(): Unit = {
    // TODO unify the message in tests
    assertMessages(errorsFromScalaCode("val v: Int = \"foo\": String"))(Error("String", "Expression of type String doesn't conform to expected type Int"))
  }

  // TODO (ScExpressionAnnotator)
  // Fine-grained type mismatch diff, SCL-15544, SCL-15481
//  def testTypeMismatchAndTypeAscriptionErrorDiff(): Unit = {
//    assertMessages(errorsFromScalaCode("val v: Option[Int] = Some(\"foo\"): Some[String]"))(Error("String", "Expression of type String doesn't conform to expected type Int")) // TODO unify the message
//  }

  // Don't show additional type mismatch when there's an error in type ascription (handled in ScTypedExpressionAnnotator), SCL-15544
  def testTypeMismatchAndTypeAscriptionInnerError(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int = 1: String"))(Error("String", "Cannot upcast Int to String"))
  }

  // Type mismatch hint, SCL-15250

  // Use type ascription to show type mismatch, SCL-15250 (invisible Error is added for statusbar message / scollbar mark / quick-fix)
  def testTypeMismatchHint(): Unit = {
    assertMessages(errorsFromScalaCode("val v: String = ()"))(Hint("()", ": Unit"),
      Error("()", "Expression of type Unit doesn't conform to expected type String"))
  }

  // Widen literal type when non-literal type is expected, SCL-15571
  def testTypeMismatchHintWiden(): Unit = {
    assertMessages(errorsFromScalaCode("val v: String = 1"))(Hint("1", ": Int"),
      Error("1", "Expression of type Int doesn't conform to expected type String"))
  }

  // Add parentheses around infix expressions
  def testTypeMismatchHintParenthesesInfix(): Unit = {
    assertMessages(errorsFromScalaCode("val v: String = 1 + 2"))(Hint("1 + 2", "("), Hint("1 + 2", "): Int"),
      Error("1 + 2", "Expression of type Int doesn't conform to expected type String"))
  }

  // Add parentheses around postfix expressions
  def testTypeMismatchHintParenthesesPostfix(): Unit = {
    assertMessages(errorsFromScalaCode("val v: String = 'c' ##"))(Hint("'c' ##", "("), Hint("'c' ##", "): Int"),
      Error("'c' ##", "Expression of type Int doesn't conform to expected type String"))
  }

  // Add parentheses around arguemnts in right-associative infix expressions
  def testTypeMismatchHintParenthesesRightAssociative(): Unit = {
    assertMessages(errorsFromScalaCode("object O { def +: (s: String) = () }; 1 +: O"))(Hint("1", "("), Hint("1", ": Int)"),
      Error("1", "Type mismatch, expected: String, actual: Int"))
  }

  // TODO test fine-grained errors
  // TODO test error tooltips

  // Method invocation, SCL-15592

  def testMethodInvocationOk(): Unit = {
    assertMessages(errorsFromScalaCode("def f(i: Int, b: Boolean): Unit = f(1, true)"))()
  }

  def testMethodInvocationMissingArgument(): Unit = {
    assertMessages(errorsFromScalaCode("def f(i: Int, b: Boolean): Unit = f(true)"))(
      Error("e)", "Unspecified value parameters: b: Boolean")) // SCL-15773
  }

  def testMethodInvocationExcessiveArgument(): Unit = {
    assertMessages(errorsFromScalaCode("def f(i: Int): Unit = f(true, 1)"))(
      Error(", 1", "Too many arguments for method f(Int)")) // SCL-15783
  }

  def testMethodInvocationMultipleTypeMismatches(): Unit = {
    assertMessages(errorsFromScalaCode("def f(i: Int, b: Boolean): Unit = f(true, 1)"))(
      Hint("true", ": Boolean"),
      Error("true", "Type mismatch, expected: Int, actual: Boolean"))
  }

  // SCL-15594
  def testMethodOverloading(): Unit = {
    assertMessages(errorsFromScalaCode("def f(i: Int) = (); def f(f: Float) = (); f(false)"))(
      Error("f", "Cannot resolve overloaded method 'f'"))
  }

  // SCL-15594
  def testMethodOverloadingParameterList(): Unit = {
    assertMessages(errorsFromScalaCode("def f(prefix: Int)(i: Int) = (); def f(prefix: Int)(f: Float) = (); f(1)(false)"))(
      Error("f", "Cannot resolve overloaded method 'f'"))
  }

  // SCL-15594
  def testMethodOverloadingApply(): Unit = {
    assertMessages(errorsFromScalaCode("object O { def apply(i: Int) = (); def apply(f: Float) = () }; O(false)"))(
      Error("O", "Cannot resolve overloaded method 'O'")) // TODO "apply", not "O"?
  }

  // Constructor invocation, SCL-15592

  def testConstructorInvocationOk(): Unit = {
    assertMessages(errorsFromScalaCode("class C(i: Int, b: Boolean); new C(1, true)"))()
  }

  def testConstructorInvocationMissingArgument(): Unit = {
    assertMessages(errorsFromScalaCode("class C(i: Int, b: Boolean); new C(true)"))(
      Error("e)", "Unspecified value parameters: b: Boolean")) // SCL-15773
  }

  def testConstructorInvocationExcessiveArgument(): Unit = {
    assertMessages(errorsFromScalaCode("class C(i: Int); new C(true, 1)"))(
      Error(", 1", "Too many arguments for constructor(Int)")) // SCL-15783 // TODO constructor name, whitespace?
  }

  def testConstructorInvocationMultipleTypeMismatches(): Unit = {
    assertMessages(errorsFromScalaCode("class C(i: Int, b: Boolean); new C(true, 1)"))(
      Hint("true", ": Boolean"),
      Error("true", "Type mismatch, expected: Int, actual: Boolean"))
  }

  // SCL-15594
  def testConstructorOverloading(): Unit = {
    assertMessages(errorsFromScalaCode("class C(i: Int) { def this(f: Float) }; new C(false)"))(
      Error("C", "Cannot resolve overloaded constructor `C`"))
  }

  // Whitespace handling (keep hint at right-hand side after appending a whitespace)

  def testTypeMismatchWhitespace1(): Unit = {
    assertMessages(errorsFromScalaCode("val v: String = 1 "))(Hint("1", ": Int", offsetDelta = 1),
      Error("1", "Expression of type Int doesn't conform to expected type String"))
  }

  def testTypeMismatchWhitespace2(): Unit = {
    assertMessages(errorsFromScalaCode("val v: String = 1  "))(Hint("1", ": Int", offsetDelta = 2),
      Error("1", "Expression of type Int doesn't conform to expected type String"))
  }

  def testTypeMismatchWhitespaceNewLine(): Unit = {
    assertMessages(errorsFromScalaCode("val v: String = 1 \n "))(Hint("1", ": Int", offsetDelta = 1),
      Error("1", "Expression of type Int doesn't conform to expected type String"))
  }

  def testTypeMismatchWhitespaceParen(): Unit = {
    assertMessages(errorsFromScalaCode("def f(i: String) = (); f(1 )"))(Hint("1", ": Int", offsetDelta = 1),
      Error("1", "Type mismatch, expected: String, actual: Int"))
  }

  // Don't show type mismatch on a term followed by a dot ("= Math."), SCL-15754

  def testTypeMismatchDot1(): Unit = {
    assertNothing(errorsFromScalaCode("val v: String = Math."))
  }

  def testTypeMismatchDot2(): Unit = {
    assertNothing(errorsFromScalaCode("val v: String = Math\n."))
  }

  def testTypeMismatchDot3(): Unit = {
    assertNothing(errorsFromScalaCode("val v: String = Math\n  ."))
  }

  // Don't show type mismatch on unapplied methods, SCL-16431

  def testTypeMismatchUnappliedMethod(): Unit = {
    assertMessages(errorsFromScalaCode("def f(i: String): Int = 1; val v: Int = f"))(
      Error("f", "Missing arguments for method f(String)"))
  }

  def testTypeMismatchUnappliedGenericMethod(): Unit = {
    assertMessages(errorsFromScalaCode("def f[T](t: T): Int = 1; val v: Int = f"))(
      Error("f", "Missing arguments for method f(T)"))
  }

  def testTypeMismatchUnappliedGenericMethodTypeArgument(): Unit = {
    assertNothing(errorsFromScalaCode("def f[T](t: T): Int = 1; val v: Int = f[Int]"))
    // TODO missing arguments?
  }
}

class TypeMismatchHighlightingTest_with_LiteralTypes extends TypeMismatchHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_13

  // Don't widen literal type when literal type is expected, SCL-15571
  def testTypeMismatchHintNotWiden(): Unit = {
    assertMessages(errorsFromScalaCode("val v: 1 = 2"))(Hint("2", ": 2"),
      Error("2", "Expression of type 2 doesn't conform to expected type 1"))
  }

  def testTypeMismatchHintNotWidenExpected(): Unit = {
    assertMessages(errorsFromScalaCode("val x: Int = 2; val v: 1 = x"))(Hint("x", ": Int"),
      Error("x", "Expression of type Int doesn't conform to expected type 1"))
  }

  // Don't narrow type when non-literal type is ascribed but literal type is expected, SCL-15571
  def testTypeMismatchAndTypeAscriptionErrorNotNarrow(): Unit = {
    // TODO unify the message in tests
    assertMessages(errorsFromScalaCode("val v: 1 = 2: Int"))(Error("Int", "Expression of type Int doesn't conform to expected type 1"))
  }

  // Widen type when non-literal type is expected, SCL-15571
  def testTypeMismatchAndTypeAscriptionErrorWiden(): Unit = {
    // TODO unify the message in tests
    assertMessages(errorsFromScalaCode("val v: Int = true: true"))(Error("true", "Expression of type Boolean doesn't conform to expected type Int"))
  }

  // Don't widen literal type when literal type is ascribed (handled in ScTypedExpressionAnnotator), SCL-15571
  def testTypeAscriptionErrorNotWiden(): Unit = {
    assertMessages(errorsFromScalaCode("1: 2"))(Error("2", "Cannot upcast 1 to 2"))
  }

  // Don't widen type when literal type is expected, SCL-15571
  def testTypeMismatchAndTypeAscriptionErrorNotWiden(): Unit = {
    // TODO unify the message in tests
    assertMessages(errorsFromScalaCode("val v: 1 = 2: 2"))(Error("2", "Expression of type 2 doesn't conform to expected type 1"))
  }
}