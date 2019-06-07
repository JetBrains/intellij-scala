package org.jetbrains.plugins.scala.annotator

class TypeMismatchHighlightingTest extends ScalaHighlightingTestBase {
  // SCL-15544
  def testTypeAscriptionOk(): Unit = {
    assertMessages(errorsFromScalaCode("1: Int"))()
  }

  // Highlight type ascription differently from type mismatch (handled in ScTypedExpressionAnnotator), SCL-15544
  def testTypeAscriptionError(): Unit = {
    assertMessages(errorsFromScalaCode("1: String"))(Error("String", "Cannot upcast Int to String"))
  }

  // Fine-grained type ascription diff, SCL-15544, SCL-15481
  def testTypeAscriptionErrorDiff(): Unit = {
    assertMessages(errorsFromScalaCode("Some(1): Option[String]"))(Error("String", "Cannot upcast Some[Int] to Option[String]"))
  }

  // SCL-15544
  def testTypeMismatchAndTypeAscriptionOk(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int = 1: Int"))()
  }

  // When present, highlight type ascription, not expression, SCL-15544
  def testTypeMismatchAndTypeAscriptionError(): Unit = {
    assertMessages(errorsFromScalaCode("val v: Int = \"foo\": String"))(Error("String", "Expression of type String doesn't conform to expected type Int")) // TODO unify the message
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
