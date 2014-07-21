package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.intellij.lang.annotations.Language
import lang.psi.api.expr.ScTypedStmt
import org.jetbrains.plugins.scala.extensions._

class TypedStatementAnnotatorTest extends SimpleTestCase {
  final val Header = "class A; class B; object A extends A; object B extends B\n"

  def testFine {
    assertMatches(messages("A: A")) {
      case Nil =>
    }
  }

  def testTypeMismatch {
    assertMatches(messages("B: A")) {
      case Error("B", TypeMismatch()) :: Nil =>
    }
  }

  def testTypeMismatchMessage {
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
    val definition = (Header + code).parse.depthFirst.findByType(classOf[ScTypedStmt]).get
    
    val annotator = new TypedStatementAnnotator() {}
    val mock = new AnnotatorHolderMock
    
    annotator.annotateTypedStatement(definition, mock, true)
    mock.annotations
  }
  
  val TypeMismatch = containsPattern("Type mismatch")

  def containsPattern(fragment: String) = new {
    def unapply(s: String) = s.contains(fragment)
  }
}