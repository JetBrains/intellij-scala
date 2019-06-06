package org.jetbrains.plugins.scala.annotator

class TypeMismatchHighlighting extends ScalaHighlightingTestBase {
  // SCL-15544
  def testTypeAscription(): Unit = {
    assertMessages(errorsFromScalaCode("1: String"))(Error("String", "Cannot upcast Int to String"))
  }
}
