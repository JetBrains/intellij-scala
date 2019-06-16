package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_13}

class TypeMismatchHighlightingTest extends ScalaHighlightingTestBase {

  override implicit val version: ScalaVersion = Scala_2_13

  // Type ascription

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

  // Expected type & type ascription

  // SCL-15544
  def testTypeMismatchAndTypeAscriptionOk(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int = 1: Int"))()
  }

  // When present, highlight type ascription, not expression, SCL-15544
  def testTypeMismatchAndTypeAscriptionError(): Unit = {
    // TODO unify the message
    assertMessages(errorsFromScalaCode("val v: Int = \"foo\": String"))(Error("String", "Expression of type String doesn't conform to expected type Int"))
  }

  // Widen type when non-literal type is expected, SCL-15571
  def testTypeMismatchAndTypeAscriptionErrorWiden(): Unit = {
    // TODO unify the message
    assertMessages(errorsFromScalaCode("val v: Int = true: true"))(Error("true", "Expression of type Boolean doesn't conform to expected type Int"))
  }

  // Don't widen type when literal type is expected, SCL-15571
  def testTypeMismatchAndTypeAscriptionErrorNotWiden(): Unit = {
    // TODO unify the message
    assertMessages(errorsFromScalaCode("val v: 1 = 2: 2"))(Error("2", "Expression of type 2 doesn't conform to expected type 1"))
  }

  // Don't narrow type when non-literal type is ascribed but literal type is expected, SCL-15571
  def testTypeMismatchAndTypeAscriptionErrorNotNarrow(): Unit = {
    // TODO unify the message
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
}
