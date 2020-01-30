package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_13}
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

class TypeMismatchHighlightingTest extends ScalaHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_13

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

  // Don't widen literal type when literal type is ascribed (handled in ScTypedExpressionAnnotator), SCL-15571
  def testTypeAscriptionErrorNotWiden(): Unit = {
    assertMessages(errorsFromScalaCode("1: 2"))(Error("2", "Cannot upcast 1 to 2"))
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

  // Widen type when non-literal type is expected, SCL-15571
  def testTypeMismatchAndTypeAscriptionErrorWiden(): Unit = {
    // TODO unify the message in tests
    assertMessages(errorsFromScalaCode("val v: Int = true: true"))(Error("true", "Expression of type Boolean doesn't conform to expected type Int"))
  }

  // Don't widen type when literal type is expected, SCL-15571
  def testTypeMismatchAndTypeAscriptionErrorNotWiden(): Unit = {
    // TODO unify the message in tests
    assertMessages(errorsFromScalaCode("val v: 1 = 2: 2"))(Error("2", "Expression of type 2 doesn't conform to expected type 1"))
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

  // Don't widen literal type when literal type is expected, SCL-15571
  def testTypeMismatchHintNotWiden(): Unit = {
    assertMessages(errorsFromScalaCode("val v: 1 = 2"))(Hint("2", ": 2"),
      Error("2", "Expression of type 2 doesn't conform to expected type 1"))
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

  def testTypeMismatchWhitespaceIf(): Unit = {
    assertMessages(errorsFromScalaCode("val v: String = if (true) 1 else \"\""))(Hint("1", ": Int", offsetDelta = 0),
      Error("1", "Expression of type Int doesn't conform to expected type String"))
  }

  def testTypeMismatchWhitespaceIfNewline(): Unit = {
    assertMessages(errorsFromScalaCode("val v: String = if (true) 1 \nelse \"\""))(Hint("1", ": Int", offsetDelta = 1),
      Error("1", "Expression of type Int doesn't conform to expected type String"))
  }

  def testTypeMismatchMultipleWhitespacesIf(): Unit = {
    assertMessages(errorsFromScalaCode("val v: String = if (true) 1  else \"\""))(Hint("1", ": Int", offsetDelta = 1),
      Error("1", "Expression of type Int doesn't conform to expected type String"))
  }

  def testTypeMismatchWhitespaceRightBrace(): Unit = {
    assertMessages(errorsFromScalaCode("object O { def ++(s: String): Int = 1 }; { O ++ 1 }"))(Hint("1", ": Int", offsetDelta = 0),
      Error("1", "Type mismatch, expected: String, actual: Int"))
  }

  def testTypeMismatchWhitespaceRightBraceNewline(): Unit = {
    assertMessages(errorsFromScalaCode("object O { def ++(s: String): Int = 1 }; { O ++ 1 \n}"))(Hint("1", ": Int", offsetDelta = 1),
      Error("1", "Type mismatch, expected: String, actual: Int"))
  }

  def testTypeMismatchMultipleWhitespacesRightBrace(): Unit = {
    assertMessages(errorsFromScalaCode("object O { def ++(s: String): Int = 1 }; { O ++ 1  }"))(Hint("1", ": Int", offsetDelta = 1),
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

  // Function literal, SCL-16904

  def testTypeMismatchFunctionLiteralNotEnoughParameters1(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => Boolean = () => true"))(Error("()", "Missing parameter: Int"))
  }

  def testTypeMismatchFunctionLiteralNotEnoughParameters2(): Unit = {
    assertMessages(errorsFromScalaCode("val v: (Int, String) => Boolean = foo => true"))(Error("o =", "Missing parameter: String"))
  }

  def testTypeMismatchFunctionLiteralNotEnoughParameters3(): Unit = {
    assertMessages(errorsFromScalaCode("val v: (Int, String) => Boolean = (foo) => true"))(Error("o)", "Missing parameter: String"))
  }

  def testTypeMismatchFunctionLiteralNotEnoughParameters4(): Unit = {
    assertMessages(errorsFromScalaCode("val v: (Int, String) => Boolean = (foo: Int) => true"))(Error("t)", "Missing parameter: String"))
  }

  def testTypeMismatchFunctionLiteralNotEnoughParameters5(): Unit = {
    assertMessages(errorsFromScalaCode("val v: (Int, String, Double) => Boolean = foo => true"))(Error("o =", "Missing parameters: String, Double"))
  }

  def testTypeMismatchFunctionLiteralTooManyParameters1(): Unit = {
    assertMessages(errorsFromScalaCode("val v: () => Boolean = foo => true"))(Error("foo", "Too many parameters"))
  }

  def testTypeMismatchFunctionLiteralTooManyParameters2(): Unit = {
    assertMessages(errorsFromScalaCode("val v: () => Boolean = (foo) => true"))(Error("(f", "Too many parameters"))
  }

  def testTypeMismatchFunctionLiteralTooManyParameters3(): Unit = {
    assertMessages(errorsFromScalaCode("val v: () => Boolean = (foo: Int) => true"))(Error("(f", "Too many parameters"))
  }

  def testTypeMismatchFunctionLiteralTooManyParameters4(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => Boolean = (foo: Int, bar: String) => true"))(Error(", b", "Too many parameters"))
  }

  def testTypeMismatchFunctionLiteralTooManyParameters5(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => Boolean = (foo: Int, bar: String, moo: Double) => true"))(Error(", b", "Too many parameters"))
  }

  def testTypeMismatchFunctionLiteralParameterTypeMismatch1(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => Boolean = (x: String) => true"))(Error("String", "Type mismatch, expected: Int, actual: String"))
  }

  def testTypeMismatchFunctionLiteralParameterTypeMismatch2(): Unit = { // TODO Why the .scala qualifier is added to the presentation?
    assertMessages(errorsFromScalaCode("val v: Option[Int] => Boolean = (x: scala.Option[String]) => true"))(Error("String", "Type mismatch, expected: Option[Int], actual: scala.Option[String]"))
  }

  def testTypeMismatchFunctionLiteralParameterTypeMismatch3(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => Boolean = (x: String, y: Double) => true"))(Error(", y", "Too many parameters"))
  }

  def testTypeMismatchFunctionLiteralParameterTypeMismatch4(): Unit = {
    assertMessages(errorsFromScalaCode("val v: (Int, String) => Boolean = (x: String, y: Int) => true"))(Error("String", "Type mismatch, expected: Int, actual: String"))
  }

  def testTypeMismatchFunctionLiteralResultTypeMismatch(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => String = x => 2"))(Hint("2", ": Int"),
      Error("2" , "Expression of type Int doesn't conform to expected type String"))
  }

  def testTypeMismatchFunctionLiteralParameterMissingAndResultTypeMismatch(): Unit = {
    assertMessages(errorsFromScalaCode("val v: (Int, Double) => String = foo => 2)"))(
      Error("o =" , "Missing parameter: Double"))
  }

  def testTypeMismatchFunctionLiteralTooManyParametersAndResultTypeMismatch(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => String = (foo, bar) => 2)"))(
      Error(", b" , "Too many parameters"))
  }

  def testTypeMismatchFunctionLiteralParameterTypeMismatchAndResultTypeMismatch(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => String = (s: String) => 2)"))(
      Error("String" , "Type mismatch, expected: Int, actual: String"))
  }

  // Function vs non-function, SCL-16904

  def testTypeMismatchFunctionTypeAndNonFunction1(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => Int = true"))(Hint("true", ": Boolean"),
      Error("true", "Expression of type Boolean doesn't conform to expected type Int => Int"))
  }

  def testTypeMismatchFunctionTypeAndNonFunction2(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => Int = 1"))(Hint("1", ": Int"),
      Error("1", "Expression of type Int doesn't conform to expected type Int => Int"))
  }

  def testTypeMismatchNonFunctionTypeAndFunctionLiteral1(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int = (x: String) => true"))(Hint("(x: String) => true", "("), Hint("(x: String) => true", "): String => Boolean"),
      Error("(x: String) => true", "Expression of type String => Boolean doesn't conform to expected type Int"))
  }

  def testTypeMismatchNonFunctionTypeAndFunctionLiteral2(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int = (x: Int) => 1"))(Hint("(x: Int) => 1", "("), Hint("(x: Int) => 1", "): Int => Int"),
      Error("(x: Int) => 1", "Expression of type Int => Int doesn't conform to expected type Int"))
  }

  def testTypeMismatchNonFunctionTypeAndFunctionLiteral3(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int = x => 1"))(Error("x", "Missing parameter type"))
  }

  // Missing parameter type, SCL-16904

  def testTypeMismatchFunctionLiteralParameterTypeInferred(): Unit = {
    assertNothing(errorsFromScalaCode("val v: Int => Boolean = x => true"))
  }

  def testTypeMismatchFunctionLiteralParameterTypeMissing1(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Any = x => true"))(Error("x", "Missing parameter type"))
  }

  def testTypeMismatchFunctionLiteralParameterTypeMissing2(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Any = (x) => true"))(Error("x", "Missing parameter type"))
  }

  def testTypeMismatchFunctionLiteralParameterTypeMissing3(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Any = (x, y) => true"))(Error("x", "Missing parameter type"))
  }

  // Function literal in a block

  def testTypeMismatchFunctionLiteralInBlockExpressionProblemWithParametersAndResutlTypeMismatch(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => String = { (i: Boolean) => 2 }"))(
      Error("Boolean" , "Type mismatch, expected: Int, actual: Boolean"))
  }

  def testTypeMismatchFunctionLiteralInBlockExpressionResultTypeMismatch1(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => String = { i => 2 }"))(Hint("2", ": Int"),
      Error("2" , "Expression of type Int doesn't conform to expected type String"))
  }

  def testTypeMismatchFunctionLiteralInBlockExpressionResultTypeMismatch2(): Unit = {
    assertMessages(errorsFromScalaCode("def f(v: Int => String) = (); f { i => 2 }"))(Hint("2", ": Int"),
      Error("2" , "Expression of type Int doesn't conform to expected type String"))
  }

  def testTypeMismatchFunctionLiteralInBlockExpressionProblemWithParametersAndResultTypeMismatchNewLine(): Unit = {
    assertMessages(errorsFromScalaCode("val x: Int => String = { (i: Boolean) => 2\n}"))(
      Error("Boolean" , "Type mismatch, expected: Int, actual: Boolean"))
  }

  // When block expression is multiline, and the problem is with the result type (but not parameters), highlight the whole literal.
  def testTypeMismatchFunctionLiteralInBlockExpressionResultTypeMismatchNewLine1(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => String = { i => 2\n}"))(Hint("}", ": Int => Int"),
      Error("}" , "Expression of type Int => Int doesn't conform to expected type Int => String"))
  }

  def testTypeMismatchFunctionLiteralInBlockExpressionResultTypeMismatchNewLine2(): Unit = {
    assertMessages(errorsFromScalaCode("def f(x: Int => String): Int = 1; f { i => 2\n}"))(Hint("}", ": Int => Int"),
      Error("}" , "Expression of type Int => Int doesn't conform to expected type Int => String"))
  }

  def testTypeMismatchFunctionLiteralInBlockExpressionResultTypeMismatchNewLine3(): Unit = {
    assertMessages(errorsFromScalaCode("def f(x: Int => String): Int = 1; f { i =>\n2 }"))(Hint("}", ": Int => Int"),
      Error("}" , "Expression of type Int => Int doesn't conform to expected type Int => String"))
  }

  // TODO The following is a workaround for SCL-16898 (Function literals: don't infer type when parameter type is not known)

  def testTypeMismatchFunctionLiteralUnresolvedReference(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int = x => x.foo"))(Error("x", "Missing parameter type"),
      Error("foo", "Cannot resolve symbol foo")) // TODO The second error is redundant
  }

  def testTypeMismatchExpandedFunctionUnresolvedReference(): Unit = { // TODO should be "Missing parameter type"
    assertMessages(errorsFromScalaCode("val v: Int = _.foo"))(Error("foo", "Cannot resolve symbol foo"))
  }

  // Plain block expression

  // While this differs from function literals, an outer type asciption is not longer than inner one
  // (as in the case of function literals), yet it doesn't interfere with potential editing
  // (in contrast to an inner one). Besides, there's the {} case.
  def testTypeMismatchSingleLineBlockExpression(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int = { \"foo\" }"))(Hint("}", ": String"),
      Error("}", "Expression of type String doesn't conform to expected type Int"))
  }

  def testTypeMismatchSingleMultilineBlockExpression(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int = { \"foo\"\n}"))(Hint("}", ": String"),
      Error("}", "Expression of type String doesn't conform to expected type Int"))
  }

  // Don't show type mismatch when there's a parser error (inside the expression, or an adjacent one)
  def testTypeMismatchExpandedParserError(): Unit = {
    assertNothing(errorsFromScalaCode("val v: String = 123`"))
  }
}
