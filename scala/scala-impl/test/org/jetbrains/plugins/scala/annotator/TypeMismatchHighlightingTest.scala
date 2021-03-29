package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

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

// See also: ScFunctionExprAnnotatorTest
class TypeMismatchHighlightingTest extends ScalaHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_13 // Literal types

  override protected def withHints = true

  // Type ascription, SCL-15544

  // SCL-15544
  def testTypeAscriptionOk(): Unit = assertErrors(
    "1: Int")

  // Highlight type ascription differently from type mismatch (handled in ScTypedExpressionAnnotator), SCL-15544
  def testTypeAscriptionError(): Unit = assertErrors(
    "(): Int",
    Error("Int", "Cannot upcast Unit to Int"))

  // Widen literal type when non-literal type is ascribed (handled in ScTypedExpressionAnnotator), SCL-15571
  def testTypeAscriptionErrorWiden(): Unit = assertErrors(
    "true: Int",
    Error("Int", "Cannot upcast Boolean to Int"))

  // Don't widen literal type when literal type is ascribed (handled in ScTypedExpressionAnnotator), SCL-15571
  def testTypeAscriptionErrorNotWiden(): Unit = assertErrors(
    "1: 2",
    Error("2", "Cannot upcast 1 to 2"))

  // Fine-grained type ascription diff, SCL-15544, SCL-15481
  def testTypeAscriptionErrorDiff(): Unit = assertErrors(
    "Some(1): Option[String]",
    Error("String", "Cannot upcast Some[Int] to Option[String]"))

  // Expected type & type ascription, SCL-15544

  // SCL-15544
  def testTypeMismatchAndTypeAscriptionOk(): Unit = assertErrors(
    "val v: Int = 1: Int")

  // When present, highlight type ascription, not expression, SCL-15544
  def testTypeMismatchAndTypeAscriptionError(): Unit = assertErrors(
    "val v: Int = \"foo\": String",
    Error("String", "Expression of type String doesn't conform to expected type Int")) // TODO unify the message in tests

  // Widen type when non-literal type is expected, SCL-15571
  def testTypeMismatchAndTypeAscriptionErrorWiden(): Unit = assertErrors(
    "val v: Int = true: true",
    Error("true", "Expression of type Boolean doesn't conform to expected type Int")) // TODO unify the message in tests

  // Don't widen type when literal type is expected, SCL-15571
  def testTypeMismatchAndTypeAscriptionErrorNotWiden(): Unit = assertErrors(
    "val v: 1 = 2: 2",
    Error("2", "Expression of type 2 doesn't conform to expected type 1")) // TODO unify the message in tests

  def testTypeMismatchHintNotWidenExpected(): Unit = assertErrors(
    "val x: Int = 2; val v: 1 = x",
    Hint("x", ": Int"),
    Error("x", "Expression of type Int doesn't conform to expected type 1"))

  // Don't narrow type when non-literal type is ascribed but literal type is expected, SCL-15571
  def testTypeMismatchAndTypeAscriptionErrorNotNarrow(): Unit = assertErrors(
    "val v: 1 = 2: Int",
    Error("Int", "Expression of type Int doesn't conform to expected type 1")) // TODO unify the message in tests

  // TODO (ScExpressionAnnotator)
  // Fine-grained type mismatch diff, SCL-15544, SCL-15481
  //  def testTypeMismatchAndTypeAscriptionErrorDiff(): Unit = assertErrors(
  //    "val v: Option[Int] = Some(\"foo\"): Some[String]",
  //    Error("String", "Expression of type String doesn't conform to expected type Int")) // TODO unify the message

  // Don't show additional type mismatch when there's an error in type ascription (handled in ScTypedExpressionAnnotator), SCL-15544
  def testTypeMismatchAndTypeAscriptionInnerError(): Unit = assertErrors(
    "val v: Int = 1: String",
    Error("String", "Cannot upcast Int to String"))

  // Type mismatch hint, SCL-15250

  // Use type ascription to show type mismatch, SCL-15250 (invisible Error is added for statusbar message / scollbar mark / quick-fix)
  def testTypeMismatchHint(): Unit = assertErrors(
    "val v: String = ()",
    Hint("()", ": Unit"),
    Error("()", "Expression of type Unit doesn't conform to expected type String"))

  // Widen literal type when non-literal type is expected, SCL-15571
  def testTypeMismatchHintWiden(): Unit = assertErrors(
    "val v: String = 1",
    Hint("1", ": Int"),
    Error("1", "Expression of type Int doesn't conform to expected type String"))

  // Don't widen literal type when literal type is expected, SCL-15571
  def testTypeMismatchHintNotWiden(): Unit = assertErrors(
    "val v: 1 = 2",
    Hint("2", ": 2"),
    Error("2", "Expression of type 2 doesn't conform to expected type 1"))

  // Add parentheses around infix expressions
  def testTypeMismatchHintParenthesesInfix(): Unit = assertErrors(
    "val v: String = 1 + 2",
    Hint("1 + 2", "("), Hint("1 + 2", "): Int"),
    Error("1 + 2", "Expression of type Int doesn't conform to expected type String"))

  // Add parentheses around postfix expressions
  def testTypeMismatchHintParenthesesPostfix(): Unit = assertErrors(
    "val v: String = 'c' ##",
    Hint("'c' ##", "("), Hint("'c' ##", "): Int"),
    Error("'c' ##", "Expression of type Int doesn't conform to expected type String"))

  // Add parentheses around arguemnts in right-associative infix expressions
  def testTypeMismatchHintParenthesesRightAssociative(): Unit = assertErrors(
    "object O { def +: (s: String) = () }; 1 +: O",
    Hint("1", "("), Hint("1", ": Int)"),
    Error("1", "Type mismatch, expected: String, actual: Int"))

  // Qualifier, SCL-16961

  def testUnqualifiedName(): Unit = assertErrors(
    "object O { class C[T]; val v: C[String] = new C[Int] }",
    Hint("new C[Int]", ": C[Int]"),
    Error("new C[Int]", "Expression of type C[Int] doesn't conform to expected type C[String]"))

  def testQualifiedName(): Unit = assertErrors(
    "object A { class C[T] }; object B { class C[T]; val v: C[Int] = new A.C[Int] }",
    Hint("new A.C[Int]", ": A.C[Int]"),
    Error("new A.C[Int]", "Expression of type A.C[Int] doesn't conform to expected type C[Int]"))

  // Type alias, SCL-18827
  def testTypeAliasSeq(): Unit = assertErrors(
    "val v: Seq[String] = Seq[Int]()", // scala.Seq = seq.collection.immutable.Seq
    Hint("Seq[Int]()", ": Seq[Int]"),
    Error("Seq[Int]()", "Expression of type Seq[Int] doesn't conform to expected type Seq[String]"))
  
  // TODO test fine-grained errors
  // TODO test error tooltips

  // Method invocation, SCL-15592

  def testMethodInvocationOk(): Unit = assertErrors(
    "def f(i: Int, b: Boolean): Unit = f(1, true)")

  def testMethodInvocationMissingArgument(): Unit = assertErrors(
    "def f(i: Int, b: Boolean): Unit = f(true)",
    Error("e)", "Unspecified value parameters: b: Boolean")) // SCL-15773

  def testMethodInvocationExcessiveArgument(): Unit = assertErrors(
    "def f(i: Int): Unit = f(true, 1)",
    Error(", 1", "Too many arguments for method f(Int)")) // SCL-15783

  def testMethodInvocationMultipleTypeMismatches(): Unit = assertErrors(
    "def f(i: Int, b: Boolean): Unit = f(true, 1)",
    Hint("true", ": Boolean"),
    Error("true", "Type mismatch, expected: Int, actual: Boolean"))

  // SCL-15594
  def testMethodOverloading(): Unit = assertErrors(
    "def f(i: Int) = (); def f(f: Float) = (); f(false)",
    Error("f", "Cannot resolve overloaded method 'f'"))

  // SCL-15594
  def testMethodOverloadingParameterList(): Unit = assertErrors(
    "def f(prefix: Int)(i: Int) = (); def f(prefix: Int)(f: Float) = (); f(1)(false)",
    Error("f", "Cannot resolve overloaded method 'f'"))

  // SCL-15594
  def testMethodOverloadingApply(): Unit = assertErrors(
    "object O { def apply(i: Int) = (); def apply(f: Float) = () }; O(false)",
    Error("O", "Cannot resolve overloaded method 'O'")) // TODO "apply", not "O"?

  // Constructor invocation, SCL-15592

  def testConstructorInvocationOk(): Unit = assertErrors(
    "class C(i: Int, b: Boolean); new C(1, true)")

  def testConstructorInvocationMissingArgument(): Unit = assertErrors(
    "class C(i: Int, b: Boolean); new C(true)",
    Error("e)", "Unspecified value parameters: b: Boolean")) // SCL-15773

  def testConstructorInvocationExcessiveArgument(): Unit = assertErrors(
    "class C(i: Int); new C(true, 1)",
    Error(", 1", "Too many arguments for constructor C(Int)")) // SCL-15783

  def testConstructorInvocationMultipleTypeMismatches(): Unit = assertErrors("" +
    "class C(i: Int, b: Boolean); new C(true, 1)",
    Hint("true", ": Boolean"),
    Error("true", "Type mismatch, expected: Int, actual: Boolean"))

  // SCL-15594
  def testConstructorOverloading(): Unit = assertErrors(
    "class C(i: Int) { def this(f: Float) }; new C(false)",
    Error("C", "Cannot resolve overloaded constructor `C`"))

  // Whitespace handling (keep hint at right-hand side after appending a whitespace)

  def testTypeMismatchWhitespace1(): Unit = assertErrors(
    "val v: String = 1 ",
    Hint("1", ": Int", offsetDelta = 1),
    Error("1", "Expression of type Int doesn't conform to expected type String"))

  def testTypeMismatchWhitespace2(): Unit = assertErrors(
    "val v: String = 1  ",
    Hint("1", ": Int", offsetDelta = 2),
    Error("1", "Expression of type Int doesn't conform to expected type String"))

  def testTypeMismatchWhitespaceNewLine(): Unit = assertErrors(
    "val v: String = 1 \n ",
    Hint("1", ": Int", offsetDelta = 1),
    Error("1", "Expression of type Int doesn't conform to expected type String"))

  def testTypeMismatchWhitespaceParen(): Unit = assertErrors(
    "def f(i: String) = (); f(1 )",
    Hint("1", ": Int", offsetDelta = 1),
    Error("1", "Type mismatch, expected: String, actual: Int"))

  def testTypeMismatchWhitespaceIf(): Unit = assertErrors(
    "val v: String = if (true) 1 else \"\"",
    Hint("1", ": Int", offsetDelta = 0),
    Error("1", "Expression of type Int doesn't conform to expected type String"))

  def testTypeMismatchWhitespaceIfNewline(): Unit = assertErrors(
    "val v: String = if (true) 1 \nelse \"\"",
    Hint("1", ": Int", offsetDelta = 1),
    Error("1", "Expression of type Int doesn't conform to expected type String"))

  def testTypeMismatchMultipleWhitespacesIf(): Unit = assertErrors(
    "val v: String = if (true) 1  else \"\"",
    Hint("1", ": Int", offsetDelta = 1),
    Error("1", "Expression of type Int doesn't conform to expected type String"))

  def testTypeMismatchWhitespaceRightBrace(): Unit = assertErrors(
    "object O { def ++(s: String): Int = 1 }; { O ++ 1 }",
    Hint("1", ": Int", offsetDelta = 0),
    Error("1", "Type mismatch, expected: String, actual: Int"))

  def testTypeMismatchWhitespaceRightBraceNewline(): Unit = assertErrors(
    "object O { def ++(s: String): Int = 1 }; { O ++ 1 \n}",
    Hint("1", ": Int", offsetDelta = 1),
    Error("1", "Type mismatch, expected: String, actual: Int"))

  def testTypeMismatchMultipleWhitespacesRightBrace(): Unit = assertErrors(
    "object O { def ++(s: String): Int = 1 }; { O ++ 1  }",
    Hint("1", ": Int", offsetDelta = 1),
    Error("1", "Type mismatch, expected: String, actual: Int"))

  // Don't show type mismatch on a term followed by a dot ("= Math."), SCL-15754

  def testTypeMismatchDot1(): Unit = assertErrors(
    "val v: String = Math.")

  def testTypeMismatchDot2(): Unit = assertErrors(
    "val v: String = Math\n.")

  def testTypeMismatchDot3(): Unit = assertErrors(
    "val v: String = Math\n  .")

  // val v: Ordering[Int] = Ordering.by(_.) // Type mismatch, expected: NotInferredT => NotInferredT, actual: Int, SCL-17206
  def testTypeMismatchDotIndentifierExpected(): Unit = assertErrors(
    "def f[T](f: T => Unit) = (); val v: java.util.Comparator[Int] = f(_.)")

  // Don't show type mismatch on unapplied methods, SCL-16431

  def testTypeMismatchUnappliedMethod(): Unit = assertErrors(
    "def f(i: String): Int = 1; val v: Int = f",
    Error("f", "Missing arguments for method f(String)"))

  def testTypeMismatchUnappliedGenericMethod(): Unit = assertErrors(
    "def f[T](t: T): Int = 1; val v: Int = f",
    Error("f", "Missing arguments for method f(T)"))

  def testTypeMismatchUnappliedGenericMethodTypeArgument(): Unit = assertErrors(
    "def f[T](t: T): Int = 1; val v: Int = f[Int]") // TODO missing arguments?

  def testTypeMismatchUnappliedExtensionMethod(): Unit = assertErrors(
    "implicit class AnyOps(val that: Any) { def f(x: Any): Unit = () }; val v: Int = 1.f",
    Error("1.f", "Missing arguments for method f(Any)")) // TODO Exclude qualifier

  // TODO Highlight "& ", missing argument
  // The & is (incorrectly) highlighted by the LanguageFeatureInspection though, so there's at least some highlighting :)
  def testTypeMismatchUnappliedMethodInfix(): Unit = assertErrors(
    "object O { def &(i: String): Unit = () }; val v: Int = O &")

  // TODO Highlight "-> ", missing argument
  def testTypeMismatchUnappliedExtensionMethodInfix(): Unit = assertErrors(
    "implicit class AnyOps(val that: Any) { def -> (x: Any): Unit = () }; val v: Int = 1 ->")

  def testTypeMismatchUnappliedCurrying(): Unit = assertErrors(
    "def f(i: Int)(s: String): Unit = (); val v: Int = f(1)",
    Error(")", "Missing argument list (s: String) for method f(Int)(String)")
  )

  def testTypeMismatchUnappliedNoExpectedType(): Unit = assertErrors(
    "def f(i: Int)(s: String): Unit = (); val v = f(1)",
    Error(")", "Missing argument list (s: String) for method f(Int)(String)")
  )

  def testTypeMismatchUnappliedEtaExpansion(): Unit = assertErrors(
    "def f(i: Int)(s: String): Unit = (); val v = f(1) _")

  def testTypeMismatchUnappliedImplicit(): Unit = assertErrors(
    "def f(i: Int)(implicit s: String): Int = 1; implicit val s = \"\"; val v: String = f(2)",
    Hint("f(2)", ": Int"),
    Error("f(2)", "Expression of type Int doesn't conform to expected type String"))

  // TODO Generalize to arbitrary expression
  // TODO The following is a workaround for SCL-16898 (Function literals: don't infer type when parameter type is not known)

  def testTypeMismatchFunctionLiteralUnresolvedReference(): Unit = assertErrors(
    "val v: Int = x => x.foo",
    Error("x", "Missing parameter type"),
    Error("foo", "Cannot resolve symbol foo")) // TODO The second error is redundant

  def testTypeMismatchExpandedFunctionUnresolvedReference(): Unit = assertErrors(
    "val v: Int = _.foo",
    Error("foo", "Cannot resolve symbol foo")) // TODO should be "Missing parameter type"

  def testTypeMismatchUnresolvedReferenceMultiple(): Unit = assertErrors(
    "val v: Int = { import scala.math.abs }",
    Hint("}", ": Unit"),
    Error("}", "Expression of type Unit doesn't conform to expected type Int"))

  // Plain block expression

  // While this differs from function literals, an outer type asciption is not longer than inner one
  // (as in the case of function literals), yet it doesn't interfere with potential editing
  // (in contrast to an inner one). Besides, there's the {} case.
  def testTypeMismatchSingleLineBlockExpression(): Unit = assertErrors(
    "val v: Int = { \"foo\" }",
    Hint("}", ": String"),
    Error("}", "Expression of type String doesn't conform to expected type Int"))

  def testTypeMismatchSingleMultilineBlockExpression(): Unit = assertErrors(
    "val v: Int = { \"foo\"\n}",
    Hint("}", ": String"),
    Error("}", "Expression of type String doesn't conform to expected type Int"))

  // Don't show type mismatch when there's a parser error (inside the expression, or an adjacent one)
  def testTypeMismatchExpandedParserError(): Unit = assertErrors(
    "val v: String = 123`")

  // Workaround for SCL-17168 (e.g. the parser error might be in the next element, so it's better to have a dedicated test for that)
  def testTypeMismatchEmptyNew(): Unit = assertErrors(
    "val v: String = new")

  // Don't show type mismatch when the expression is of a singleton type that has an apply method, while a non-singleton type is expected, SCL-17669

  def testSingletonTypeHasApply(): Unit = assertErrors(
    "object O { def apply(p: Int) = 1 }; val v: Int = O",
    Error("O", "Unspecified value parameters: p: Int"))

  def testCaseClass(): Unit = assertErrors(
    "case class C(p: Int); val v: C = C",
    Error("C", "Unspecified value parameters: p: Int"))

  def testSingletonTypeExpected(): Unit = assertErrors(
    "object A { def apply(p: Int) = 1 }; object B; val v: B.type = A",
    Hint("A", ": A.type"),
    Error("A", "Expression of type A.type doesn't conform to expected type B.type"))

  def testSingletonTypeHasNoApply(): Unit = assertErrors(
    "object O; val v: Int = O",
    Hint("O", ": O.type"),
    Error("O", "Expression of type O.type doesn't conform to expected type Int"))

  // Can we extrapolate that to FunctionN types?
  def testNonSingletonTypeHasApply(): Unit = assertErrors(
    "val x = (p: Int) => 1; val v: Int = x",
    Hint("x", ": Int => Int"),
    Error("x", "Expression of type Int => Int doesn't conform to expected type Int"))
  
  // Incomplete if-then-else, #SCL-18862

  // TODO Highlight "1 ", else expected (currently there's no highlighting for "if", "if ()", and "if (true)" cases anyway) 
  def testIncompleteIfThenElse(): Unit = assertErrors(
    "val v: Int = if (true) 1")
}
