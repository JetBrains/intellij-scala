package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.intellij.lang.annotations.Language
import lang.psi.api.expr.ScAssignStmt

/**
 * Pavel.Fatin, 18.05.2010
 */

class AssignmentAnnotatorTest extends SimpleTestCase {
  val Header = """
  class A; class B
  object A extends A; object B extends B
  """
    
  def testVariable {
    assertMatches(messages("var v = A; v = A")) {
      case Nil =>
    }
    assertMatches(messages("var v = A; v = B")) {
      case Error("B", TypeMismatch()) :: Nil =>
    }
  }
  
  def testImplicitConversion {
    assertMatches(messages("implicit def toA(b: B) = A; var v = A; v = B")) {
      case Nil =>
    }
  }
  
  def testValue {
    assertMatches(messages("val v = A; v = A")) {
      case Error("v = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("val v = A; v = B")) {
      case Error("v = B", ReassignmentToVal()) :: Nil =>
    }
  }
  
  def testFunctionParameter {
    assertMatches(messages("def f(p: A) { p = A }")) {
      case Error("p = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("def f(p: A) { p = B }")) {
      case Error("p = B", ReassignmentToVal()) :: Nil =>
    }
  }
  
  def testClassParameter {
    assertMatches(messages("class C(p: A) { p = A }")) {
      case Nil =>
    }
    // TODO right expression "B" must have expected type 
//    assertMatches(messages("class C(p: A) { p = B }")) {
//      case Error("B", TypeMismatch()) :: Nil =>
//    }
  }
  
  def testClassVariableParameter {
    assertMatches(messages("class C(var p: A) { p = A }")) {
      case Nil =>
    }
  // TODO right expression "B" must have expected type    
//    assertMatches(messages("class C(var p: A) { p = B }")) {
//      case Error("B", TypeMismatch()) :: Nil =>
//    }
  }

  def testClassValueParameter {
    assertMatches(messages("class C(val p: A) { p = A }")) {
      case Error("p = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("class C(val p: A) { p = B }")) {
      case Error("p = B", ReassignmentToVal()) :: Nil =>
    }
  }
  
  def testFunctionLiteralParameter {
    assertMatches(messages("(p: A) => { p = A }")) {
      case Error("p = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("(p: A) => { p = B }")) {
      case Error("p = B", ReassignmentToVal()) :: Nil =>
    }
  }
  
  def testParameterInsideBlock {
    assertMatches(messages("{ p: A => p = A }")) {
      case Error("p = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("{ p: A => p = B }")) {
      case Error("p = B", ReassignmentToVal()) :: Nil =>
    }
  }
  
  def testForComprehensionGenerator {
    assertMatches(messages("for(v: A <- null) { v = A }")) {
      case Error("v = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("for(v: A <- null) { v = B }")) {
      case Error("v = B", ReassignmentToVal()) :: Nil =>
    }
  }
  
  def testForComprehensionEnumerator {
    assertMatches(messages("for(x <- null; v = A) { v = A }")) {
      case Error("v = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("for(x <- null; v = A) { v = B }")) {
      case Error("v = B", ReassignmentToVal()) :: Nil =>
    }
  }
  
  def testCaseClause {
    assertMatches(messages("A match { case v: A => v = A }")) {
      case Error("v = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("A match { case v: A => v = B }")) {
      case Error("v = B", ReassignmentToVal()) :: Nil =>
    }
  }
  
  def messages(@Language("Scala") code: String): List[Message] = {
    val assignment = (Header + code).parse.depthFirst.findByType(classOf[ScAssignStmt]).get
    
    val annotator = new AssignmentAnnotator() {}
    val mock = new AnnotatorHolderMock
    
    annotator.annotateAssignment(assignment, mock, true)
    mock.annotations
  }
  
  val TypeMismatch = startWith("Type mismatch")
  val ReassignmentToVal = startWith("Reassignment to val")

  def startWith(fragment: String) = new {
    def unapply(s: String) = s.startsWith(fragment)
  }
}