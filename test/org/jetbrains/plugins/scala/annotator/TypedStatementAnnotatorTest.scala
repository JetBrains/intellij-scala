package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTypedStmt

class TypedStatementAnnotatorTest extends SimpleTestCase {
  final val Header = "class A; class B; object A extends A; object B extends B\n"

  def testFine() {
    assertMatches(messages("A: A")) {
      case Nil =>
    }
  }

  def testTypeMismatch() {
    assertMatches(messages("B: A")) {
      case Error("B", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeMismatchMessage() {
    assertMatches(messages("B: A")) {
      case Error(_, "Type mismatch, found: B.type, required: A") :: Nil =>
    }
  }

  //todo: requires Function1 trait in scope
  /*def testImplicitConversion {
    assertMatches(messages("implicit def toA(b: B) = A; B: A")) {
      case Nil =>
    }
  }*/


  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val file = (Header + code).parse
    val definition = file.depthFirst.findByType(classOf[ScTypedStmt]).get
    
    val annotator = new TypedStatementAnnotator() {}
    val mock = new AnnotatorHolderMock(file)
    annotator.annotateTypedStatement(definition, mock, highlightErrors = true)
    mock.annotations
  }
  
  val TypeMismatch = ContainsPattern("Type mismatch")
}
