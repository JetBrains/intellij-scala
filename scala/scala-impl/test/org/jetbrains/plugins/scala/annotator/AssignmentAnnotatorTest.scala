package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator.element.ScAssignmentAnnotator
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAssignment

class AssignmentAnnotatorTest extends AnnotatorSimpleTestCase {
  final val Header = """
  class A; class B
  object A extends A; object B extends B
  """

  def testVariable(): Unit = {
    assertMatches(messages("var v = A; v = A")) {
      case Nil =>
    }
    assertMatches(messages("var v = A; v = B")) {
      case Error("B", TypeMismatch()) :: Nil =>
    }
  }

  //todo: requires Function1 trait in scope
  /*def testImplicitConversion {
    assertMatches(messages("implicit def toA(b: B) = A; var v = A; v = B")) {
      case Nil =>
    }
  }*/
  
  def testValue(): Unit = {
    assertMatches(messages("val v = A; v = A")) {
      case Error("v = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("val v = A; v = B")) {
      case Error("v = B", ReassignmentToVal()) :: Nil =>
    }
  }
  
  def testFunctionParameter(): Unit = {
    assertMatches(messages("def f(p: A) { p = A }")) {
      case Error("p = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("def f(p: A) { p = B }")) {
      case Error("p = B", ReassignmentToVal()) :: Nil =>
    }
  }
  
  def testClassParameter(): Unit = {
    assertMatches(messages("case class C(var p: A) { p = A }")) {
      case Nil =>
    }
    assertMatches(messages("class C(p: A) { p = B }")) {
      case Error("p = B", ReassignmentToVal()) :: Nil =>
    }
  }
  
  def testClassVariableParameter(): Unit = {
    assertMatches(messages("class C(var p: A) { p = A }")) {
      case Nil =>
    }
  // TODO right expression "B" must have expected type    
//    assertMatches(messages("class C(var p: A) { p = B }")) {
//      case Error("B", TypeMismatch()) :: Nil =>
//    }
  }

  def testClassValueParameter(): Unit = {
    assertMatches(messages("class C(val p: A) { p = A }")) {
      case Error("p = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("class C(val p: A) { p = B }")) {
      case Error("p = B", ReassignmentToVal()) :: Nil =>
    }
  }
  
  def testFunctionLiteralParameter(): Unit = {
    assertMatches(messages("(p: A) => { p = A }")) {
      case Error("p = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("(p: A) => { p = B }")) {
      case Error("p = B", ReassignmentToVal()) :: Nil =>
    }
  }
  
  //TODO fails on server
//  def testParameterInsideBlock {
//    assertMatches(messages("{ p: A => p = A }")) {
//      case Error("p = A", ReassignmentToVal()) :: Nil =>
//    }
//    assertMatches(messages("{ p: A => p = B }")) {
//      case Error("p = B", ReassignmentToVal()) :: Nil =>
//    }
//  }
  
  def testForComprehensionGenerator(): Unit = {
    assertMatches(messages("for(v: A <- null) { v = A }")) {
      case Error("v = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("for(v: A <- null) { v = B }")) {
      case Error("v = B", ReassignmentToVal()) :: Nil =>
    }
  }
  
  def testForComprehensionBinding(): Unit = {
    assertMatches(messages("for(x <- null; v = A) { v = A }")) {
      case Error("v = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("for(x <- null; v = A) { v = B }")) {
      case Error("v = B", ReassignmentToVal()) :: Nil =>
    }
  }
  
  def testCaseClause(): Unit = {
    assertMatches(messages("A match { case v: A => v = A }")) {
      case Error("v = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("A match { case v: A => v = B }")) {
      case Error("v = B", ReassignmentToVal()) :: Nil =>
    }
  }

  def testNamedParameterClause(): Unit = {
    assertMatches(messages("def blerg(a: Any)= 0; blerg(a = 0)")) {
      case Nil =>
    }
  }

  def testUpdateOkay(): Unit = {
    assertMatches(messages("val a = new { def update(x: Int): Unit = () }; a() = 1")) {
      case Nil =>
    }
  }
  
  def testVarInsideVar(): Unit = {
    assertMatches(messages("val x = { var a = A; a = A }")) {
      case Nil =>
    }
    assertMatches(messages("val x = { var a = A; a = B }")) {
      case Error("B", TypeMismatch()) :: Nil =>
    }
  }

  def testVarInsideTemplateAssignedToVal(): Unit = {
    assertMatches(messages("val outer = new { var a = (); a = () }")) {
      case Nil =>
    }
  }
  
  def testSetter(): Unit = {
    assertMatches(messages("def a = A; def a_=(x: A) {}; a = A")) {
      case Nil =>
    }
    assertMatches(messages("def a(implicit b: B) = A; def a_=(x: A) {}; a = A")) {
      case Nil =>
    }
    assertMatches(messages("def a() = A; def a_=(x: A) {}; a = A")) {
      case Error("a = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("val a = A; def a_=(x: A) {}; a = A")) {
      case Error("a = A", ReassignmentToVal()) :: Nil =>
    }
    assertMatches(messages("def a = A; def a_=(x: A) {}; a = B")) {
      case Error("B", TypeMismatch()) :: Nil =>
    }
    assertMatches(messages("def `a` = A; def a_=(x: A) {}; a = A")) {
      case Nil =>
    }
  }

  // SCL-17962
  def testIllegalAssignments(): Unit = {
    def assertIllegalAssignment(code: String): Unit =
      assertMatches(messages("class C; val a,b = 0; def f() = ();" + code)) {
        case Error("=", IllegalAssignmentTarget()) :: Nil =>
      }

    assertIllegalAssignment("5 = 4")
    assertIllegalAssignment("{ } = 8")
    assertIllegalAssignment("() = 4")
    assertIllegalAssignment("(5) = 4")
    assertIllegalAssignment("(a, b) = (1, 2)")
    assertIllegalAssignment("new C = 3")
    assertIllegalAssignment("1 + 2 = 3")
  }

  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val file = (Header + code).parse
    val assignment = file.depthFirst().findByType[ScAssignment].get

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    ScAssignmentAnnotator.annotate(assignment)
    mock.annotations
  }

  val TypeMismatch = StartWith("Type mismatch")
  val ReassignmentToVal = StartWith("Reassignment to val")
  val IllegalAssignmentTarget = StartWith(ScalaBundle.message("illegal.assignment.target"))

  case class StartWith(fragment: String) {
    def unapply(s: String): Boolean = s.startsWith(fragment)
  }
}