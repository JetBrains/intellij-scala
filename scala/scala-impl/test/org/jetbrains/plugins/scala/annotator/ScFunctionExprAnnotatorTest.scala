package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.ScalaVersion.Scala_2_12

// Technically, it's "type mismatch", but we can do better than scalac.
// See also: TypeMismatchHighlightingTest
class ScFunctionExprAnnotatorTest extends ScalaHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_12 // SAMs

  override protected def withHints = true

  // Function literal, SCL-16904

  def notEnoughParameters1(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => Boolean = () => true"))(Error("()", "Missing parameter: Int"))
  }

  def notEnoughParameters2(): Unit = {
    assertMessages(errorsFromScalaCode("val v: (Int, String) => Boolean = foo => true"))(Error("o =", "Missing parameter: String"))
  }

  def notEnoughParameters3(): Unit = {
    assertMessages(errorsFromScalaCode("val v: (Int, String) => Boolean = (foo) => true"))(Error("o)", "Missing parameter: String"))
  }

  def notEnoughParameters4(): Unit = {
    assertMessages(errorsFromScalaCode("val v: (Int, String) => Boolean = (foo: Int) => true"))(Error("t)", "Missing parameter: String"))
  }

  def notEnoughParameters5(): Unit = {
    assertMessages(errorsFromScalaCode("val v: (Int, String, Double) => Boolean = foo => true"))(Error("o =", "Missing parameters: String, Double"))
  }

  def tooManyParameters1(): Unit = {
    assertMessages(errorsFromScalaCode("val v: () => Boolean = foo => true"))(Error("foo", "Too many parameters"))
  }

  def tooManyParameters2(): Unit = {
    assertMessages(errorsFromScalaCode("val v: () => Boolean = (foo) => true"))(Error("(f", "Too many parameters"))
  }

  def tooManyParameters3(): Unit = {
    assertMessages(errorsFromScalaCode("val v: () => Boolean = (foo: Int) => true"))(Error("(f", "Too many parameters"))
  }

  def tooManyParameters4(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => Boolean = (foo: Int, bar: String) => true"))(Error(", b", "Too many parameters"))
  }

  def tooManyParameters5(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => Boolean = (foo: Int, bar: String, moo: Double) => true"))(Error(", b", "Too many parameters"))
  }

  def parameterTypeMismatch1(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => Boolean = (x: String) => true"))(Error("String", "Type mismatch, expected: Int, actual: String"))
  }

  def parameterTypeMismatch2(): Unit = { // TODO Why the .scala qualifier is added to the presentation?
    assertMessages(errorsFromScalaCode("val v: Option[Int] => Boolean = (x: scala.Option[String]) => true"))(Error("String", "Type mismatch, expected: Option[Int], actual: scala.Option[String]"))
  }

  def parameterTypeMismatch3(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => Boolean = (x: String, y: Double) => true"))(Error(", y", "Too many parameters"))
  }

  def parameterTypeMismatch4(): Unit = {
    assertMessages(errorsFromScalaCode("val v: (Int, String) => Boolean = (x: String, y: Int) => true"))(Error("String", "Type mismatch, expected: Int, actual: String"))
  }

  def resultTypeMismatch(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => String = x => 2"))(Hint("2", ": Int"),
      Error("2" , "Expression of type Int doesn't conform to expected type String"))
  }

  def parameterMissingAndResultTypeMismatch(): Unit = {
    assertMessages(errorsFromScalaCode("val v: (Int, Double) => String = foo => 2)"))(
      Error("o =" , "Missing parameter: Double"))
  }

  def tooManyParametersAndResultTypeMismatch(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => String = (foo, bar) => 2)"))(
      Error(", b" , "Too many parameters"))
  }

  def parameterTypeMismatchAndResultTypeMismatch(): Unit = {
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

  // SAMs

  def testTypeMismatchSam(): Unit = {
    assertMessages(errorsFromScalaCode("trait SAM { def f(s: String): Int }; val v: SAM = s => 1"))()
  }

  def testTypeMismatchSamNotEnoughParameters(): Unit = {
    assertMessages(errorsFromScalaCode("trait SAM { def f(s: String): Int }; val v: SAM = () => 1"))(
      Error("()", "Missing parameter: String"))
  }

  def testTypeMismatchSamTooManyParameters(): Unit = {
    assertMessages(errorsFromScalaCode("trait SAM { def f(s: String): Int }; val v: SAM = (s: String, b: Boolean) => 1"))(
      Error(", b", "Too many parameters"))
  }

  def testTypeMismatchSamParameterTypeMismatch(): Unit = {
    assertMessages(errorsFromScalaCode("trait SAM { def f(s: String): Int }; val v: SAM = (s: Boolean) => 1"))(
      Error("Boolean", "Type mismatch, expected: String, actual: Boolean"))
  }

  def testTypeMismatchSamResultTypeMimatch(): Unit = {
    assertMessages(errorsFromScalaCode("trait SAM { def f(s: String): Int }; val v: SAM = s => false"))(
      Hint("s => false", "("), Hint("s => false", "): String => Boolean"),
      Error("s => false", "Expression of type String => Boolean doesn't conform to expected type SAM"))
  }

  // Missing parameter type, SCL-16904

  def parameterTypeInferred(): Unit = {
    assertNothing(errorsFromScalaCode("val v: Int => Boolean = x => true"))
  }

  def parameterTypeMissing1(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Any = x => true"))(Error("x", "Missing parameter type"))
  }

  def parameterTypeMissing2(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Any = (x) => true"))(Error("x", "Missing parameter type"))
  }

  def parameterTypeMissing3(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Any = (x, y) => true"))(Error("x", "Missing parameter type"))
  }

  // Function literal in a block

  def inBlockExpressionProblemWithParametersAndResutlTypeMismatch(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => String = { (i: Boolean) => 2 }"))(
      Error("Boolean" , "Type mismatch, expected: Int, actual: Boolean"))
  }

  def inBlockExpressionResultTypeMismatch1(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => String = { i => 2 }"))(Hint("2", ": Int"),
      Error("2" , "Expression of type Int doesn't conform to expected type String"))
  }

  def inBlockExpressionResultTypeMismatch2(): Unit = {
    assertMessages(errorsFromScalaCode("def f(v: Int => String) = (); f { i => 2 }"))(Hint("2", ": Int"),
      Error("2" , "Expression of type Int doesn't conform to expected type String"))
  }

  def inBlockExpressionProblemWithParametersAndResultTypeMismatchNewLine(): Unit = {
    assertMessages(errorsFromScalaCode("val x: Int => String = { (i: Boolean) => 2\n}"))(
      Error("Boolean" , "Type mismatch, expected: Int, actual: Boolean"))
  }

  // When block expression is multiline, and the problem is with the result type (but not parameters), highlight the whole literal.
  def inBlockExpressionResultTypeMismatchNewLine1(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int => String = { i => 2\n}"))(Hint("}", ": Int => Int"),
      Error("}" , "Expression of type Int => Int doesn't conform to expected type Int => String"))
  }

  def inBlockExpressionResultTypeMismatchNewLine2(): Unit = {
    assertMessages(errorsFromScalaCode("def f(x: Int => String): Int = 1; f { i => 2\n}"))(Hint("}", ": Int => Int"),
      Error("}" , "Expression of type Int => Int doesn't conform to expected type Int => String"))
  }

  def inBlockExpressionResultTypeMismatchNewLine3(): Unit = {
    assertMessages(errorsFromScalaCode("def f(x: Int => String): Int = 1; f { i =>\n2 }"))(Hint("}", ": Int => Int"),
      Error("}" , "Expression of type Int => Int doesn't conform to expected type Int => String"))
  }

  // Function literal with type ascription

  def resutlTypeMismatchAndTypeAscription(): Unit = {
    assertMessages(errorsFromScalaCode("((i: Int) => false): Int => String"))(
      Error("String" , "Cannot upcast Int => Boolean to Int => String"))
  }

  def resutlTypeMismatchAndTypeAscriptionBlock(): Unit = {
    assertMessages(errorsFromScalaCode("{ (i: Int) => false }: Int => String"))(
      Error("String" , "Cannot upcast Int => Boolean to Int => String"))
  }
}
