package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.ScalaVersion.Scala_2_12

// Technically, it's "type mismatch", but we can do better than the scalac, SCL-16904
// See also: TypeMismatchHighlightingTest
class ScFunctionExprAnnotatorTest extends ScalaHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_12 // SAMs

  override protected def withHints = true

  def testNotEnoughParameters1(): Unit = assertErrors(
    "val v: Int => Boolean = () => true",
    Error("()", "Missing parameter: Int"))

  def testNotEnoughParameters2(): Unit = assertErrors(
    "val v: (Int, String) => Boolean = foo => true",
    Error("o =", "Missing parameter: String"))

  def testNotEnoughParameters3(): Unit = assertErrors(
    "val v: (Int, String) => Boolean = (foo) => true",
    Error("o)", "Missing parameter: String"))

  def testNotEnoughParameters4(): Unit = assertErrors(
    "val v: (Int, String) => Boolean = (foo: Int) => true",
    Error("t)", "Missing parameter: String"))

  def testNotEnoughParameters5(): Unit = assertErrors(
    "val v: (Int, String, Double) => Boolean = foo => true",
    Error("o =", "Missing parameters: String, Double"))

  def testTooManyParameters1(): Unit = assertErrors(
    "val v: () => Boolean = foo => true",
    Error("foo", "Too many parameters"))

  def testTooManyParameters2(): Unit = assertErrors(
    "val v: () => Boolean = (foo) => true",
    Error("(f", "Too many parameters"))

  def testTooManyParameters3(): Unit = assertErrors(
    "val v: () => Boolean = (foo: Int) => true",
    Error("(f", "Too many parameters"))

  def testTooManyParameters4(): Unit = assertErrors(
    "val v: Int => Boolean = (foo: Int, bar: String) => true",
    Error(", b", "Too many parameters"))

  def testTooManyParameters5(): Unit = assertErrors(
    "val v: Int => Boolean = (foo: Int, bar: String, moo: Double) => true",
    Error(", b", "Too many parameters"))

  def testParameterTypeMismatch1(): Unit = assertErrors(
    "val v: Int => Boolean = (x: String) => true",
    Error("String", "Type mismatch, expected: Int, actual: String"))

  def testParameterTypeMismatch2(): Unit = assertErrors(
    "val v: Option[Int] => Boolean = (x: scala.Option[String]) => true", // TODO Why the .scala qualifier is added to the presentation?
    Error("String", "Type mismatch, expected: Option[Int], actual: scala.Option[String]"))

  def testParameterTypeMismatch3(): Unit = assertErrors(
    "val v: Int => Boolean = (x: String, y: Double) => true",
    Error(", y", "Too many parameters"))

  def testParameterTypeMismatch4(): Unit = assertErrors(
    "val v: (Int, String) => Boolean = (x: String, y: Int) => true",
    Error("String", "Type mismatch, expected: Int, actual: String"))

  def testParameterContravariance1(): Unit = assertErrors(
    "val v: Any => Boolean = (x: String) => true",
    Error("String", "Type mismatch, expected: Any, actual: String"))

  def testParameterContravariance2(): Unit = assertErrors(
    "val v: String => Boolean = (x: Any) => true")

  def testParameterContravariance3(): Unit = assertErrors(
    "val v: Option[Any] => Boolean = (x: scala.Option[String]) => true", // TODO Why the .scala qualifier is added to the presentation?
    Error("String", "Type mismatch, expected: Option[Any], actual: scala.Option[String]"))

  def testResultTypeMismatch(): Unit = assertErrors(
    "val v: Int => String = x => 2",
    Hint("2", ": Int"),
    Error("2", "Expression of type Int doesn't conform to expected type String"))

  def testResultTypeMismatchCovariance1(): Unit = assertErrors(
    "val v: Int => String = x => new AnyRef {}",
    Hint("new AnyRef {}", ": AnyRef"),
    Error("new AnyRef {}", "Expression of type AnyRef doesn't conform to expected type String"))

  def testResultTypeMismatchCovariance2(): Unit = assertErrors(
    "val v: Int => Any = x => 2")

  def testParameterMissingAndResultTypeMismatch(): Unit = assertErrors(
    "val v: (Int, Double) => String = foo => 2)",
    Error("o =", "Missing parameter: Double"))

  def testTooManyParametersAndResultTypeMismatch(): Unit = assertErrors(
    "val v: Int => String = (foo, bar) => 2)",
    Error(", b", "Too many parameters"))

  def testParameterTypeMismatchAndResultTypeMismatch(): Unit = assertErrors(
    "val v: Int => String = (s: String) => 2)",
    Error("String", "Type mismatch, expected: Int, actual: String"))

  // Function vs non-function, SCL-16904

  def testFunctionTypeAndNonFunction1(): Unit = assertErrors(
    "val v: Int => Int = true",
    Hint("true", ": Boolean"),
    Error("true", "Expression of type Boolean doesn't conform to expected type Int => Int"))

  def testFunctionTypeAndNonFunction2(): Unit = assertErrors(
    "val v: Int => Int = 1",
    Hint("1", ": Int"),
    Error("1", "Expression of type Int doesn't conform to expected type Int => Int"))

  def testNonFunctionTypeAndFunctionLiteral1(): Unit = assertErrors(
    "val v: Int = (x: String) => true",
    Hint("(x: String) => true", "("), Hint("(x: String) => true", "): String => Boolean"),
    Error("(x: String) => true", "Expression of type String => Boolean doesn't conform to expected type Int"))

  def testNonFunctionTypeAndFunctionLiteral2(): Unit = assertErrors(
    "val v: Int = (x: Int) => 1",
    Hint("(x: Int) => 1", "("), Hint("(x: Int) => 1", "): Int => Int"),
    Error("(x: Int) => 1", "Expression of type Int => Int doesn't conform to expected type Int"))

  def testNonFunctionTypeAndFunctionLiteral3(): Unit = assertErrors(
    "val v: Int = x => 1",
    Error("x", "Missing parameter type"))

  // SAMs

  def testTypeMismatchSam(): Unit = assertErrors(
    "trait SAM { def testF(s: String): Int }; val v: SAM = s => 1")

  def testTypeMismatchSamNotEnoughParameters(): Unit = assertErrors(
    "trait SAM { def testF(s: String): Int }; val v: SAM = () => 1",
    Error("()", "Missing parameter: String"))

  def testTypeMismatchSamTooManyParameters(): Unit = assertErrors(
    "trait SAM { def testF(s: String): Int }; val v: SAM = (s: String, b: Boolean) => 1",
    Error(", b", "Too many parameters"))

  def testTypeMismatchSamParameterTypeMismatch(): Unit = assertErrors(
    "trait SAM { def testF(s: String): Int }; val v: SAM = (s: Boolean) => 1",
    Error("Boolean", "Type mismatch, expected: String, actual: Boolean"))

  def testTypeMismatchSamResultTypeMimatch(): Unit = assertErrors(
    "trait SAM { def testF(s: String): Int }; val v: SAM = s => false",
    Hint("s => false", "("), Hint("s => false", "): String => Boolean"),
    Error("s => false", "Expression of type String => Boolean doesn't conform to expected type SAM"))

  // Missing parameter type, SCL-16904

  def testParameterTypeInferred(): Unit = assertErrors(
    "val v: Int => Boolean = x => true")

  def testParameterTypeMissing1(): Unit = assertErrors(
    "val v: Any = x => true",
    Error("x", "Missing parameter type"))

  def testParameterTypeMissing2(): Unit = assertErrors(
    "val v: Any = (x) => true",
    Error("x", "Missing parameter type"))

  def testParameterTypeMissing3(): Unit = assertErrors(
    "val v: Any = (x, y) => true",
    Error("x", "Missing parameter type"))

  // Function literal in a block

  def testInBlockExpressionProblemWithParametersAndResutlTypeMismatch(): Unit = assertErrors(
    "val v: Int => String = { (i: Boolean) => 2 }",
    Error("Boolean", "Type mismatch, expected: Int, actual: Boolean"))

  def testInBlockExpressionResultTypeMismatch1(): Unit = assertErrors(
    "val v: Int => String = { i => 2 }",
    Hint("2", ": Int"),
    Error("2", "Expression of type Int doesn't conform to expected type String"))

  def testInBlockExpressionResultTypeMismatch2(): Unit = assertErrors(
    "def f(v: Int => String) = (); f { i => 2 }",
    Hint("2", ": Int"),
    Error("2", "Expression of type Int doesn't conform to expected type String"))

  def testInBlockExpressionProblemWithParametersAndResultTypeMismatchNewLine(): Unit = assertErrors(
    "val x: Int => String = { (i: Boolean) => 2\n}",
    Error("Boolean", "Type mismatch, expected: Int, actual: Boolean"))

  // When block expression is multiline, and the problem is with the result type (but not parameters), highlight the whole literal.
  def testInBlockExpressionResultTypeMismatchNewLine1(): Unit = assertErrors(
    "val v: Int => String = { i => 2\n}",
    Hint("}", ": Int => Int"),
    Error("}", "Expression of type Int => Int doesn't conform to expected type Int => String"))

  def testInBlockExpressionResultTypeMismatchNewLine2(): Unit = assertErrors(
    "def f(x: Int => String): Int = 1; f { i => 2\n}",
    Hint("}", ": Int => Int"),
    Error("}", "Expression of type Int => Int doesn't conform to expected type Int => String"))

  def testInBlockExpressionResultTypeMismatchNewLine3(): Unit = assertErrors(
    "def f(x: Int => String): Int = 1; f { i =>\n2 }",
    Hint("}", ": Int => Int"),
    Error("}", "Expression of type Int => Int doesn't conform to expected type Int => String"))

  // Function literal with type ascription

  def testResutlTypeMismatchAndTypeAscription(): Unit = assertErrors(
    "((i: Int) => false): Int => String",
    Error("String", "Cannot upcast Int => Boolean to Int => String"))

  def testResutlTypeMismatchAndTypeAscriptionBlock(): Unit = assertErrors(
    "{ (i: Int) => false }: Int => String",
    Error("String", "Cannot upcast Int => Boolean to Int => String"))

  // Implicit conversion

  def testImplicitConversion(): Unit = assertErrors(
    "implicit def convert(f: () => Int): String => Int = ???; val v: String => Int = () => 1")
}
