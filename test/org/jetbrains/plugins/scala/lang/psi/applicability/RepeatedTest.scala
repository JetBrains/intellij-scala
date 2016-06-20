package org.jetbrains.plugins.scala
package lang.psi.applicability

import org.jetbrains.plugins.scala.lang.psi.types._

/**
 * Pavel.Fatin, 18.05.2010
 */

class RepeatedTest extends ApplicabilityTestBase {
  def testMalformedDefinition() {
    assertProblems("(a: A*, b: B)", "(A, B)") {
      case MalformedDefinition() :: Nil =>
    }
    assertProblems("(a: A*, b: B)", "(A)") {
      case MalformedDefinition() :: Nil =>
    }
    assertProblems("(a: A*, b: B*)", "(A, B)") {
      case MalformedDefinition() :: Nil =>
    }
    assertProblems("(a: A, b: B*, c: C)", "(A, B, C)") {
      case MalformedDefinition() :: Nil =>
    }
    assertProblems("(a: A, b: B*, c: C*)", "(A, B, C)") {
      case MalformedDefinition() :: Nil =>
    }
  }
  
  def testMalformedDefinitionClauses() {
    assertProblems("(a: A)(b: B*, c: C)", "(A)(B, C)") {
      case MalformedDefinition() :: Nil =>
    }
  }
  
  def testValidDefinition() {
    assertProblems("(a: A*)(b: B)", "(A)(B)") {
      case Nil =>
    }
  }
  
  def testFineSingle() {
    assertProblems("(a: A*)", "()") {
      case Nil =>
    }
    assertProblems("(a: A*)", "(A)") {
      case Nil =>
    }
    assertProblems("(a: A*)", "(A, A)") {
      case Nil =>
    }
  }
  
  def testFineSecond() {
    assertProblems("(a: A, b: B*)", "(A)") {
      case Nil =>
    }
    assertProblems("(a: A, b: B*)", "(A, B)") {
      case Nil =>
    }
    assertProblems("(a: A, b: B*)", "(A, B, B)") {
      case Nil =>
    }
  }
  
  def testFineSecondSameType() {
    assertProblems("(a: A, b: A*)", "(A)") {
      case Nil =>
    }
    assertProblems("(a: A, b: A*)", "(A, A)") {
      case Nil =>
    }
    assertProblems("(a: A, b: A*)", "(A, A, A)") {
      case Nil =>
    }
  }
  
  def testFineThird() {
    assertProblems("(a: A, b: B, c: C*)", "(A, B)") {
      case Nil =>
    }
    assertProblems("(a: A, b: B, c: C*)", "(A, B, C)") {
      case Nil =>
    }
    assertProblems("(a: A, b: B, c: C*)", "(A, B, C, C)") {
      case Nil =>
    }
  }
  
  /*def testDoesNotTakeParameters {
    assertProblems("", "(Seq(A): _*)") {
      case DoesNotTakeParameters() :: Nil =>
    }
  }*/
  
  def testMissedArguments() {
    assertProblemsFunction("", "(a: A*)", "") {
      case MissedParametersClause(_) :: Nil =>
    }
    assertProblemsConstructor("", "(a: A*)", "") {
      case Nil =>
    }
  }
  
  def testTypeMismatch() {
    assertProblems("(a: A*)", "(B)") {
      case TypeMismatch(Expression("B"), Type("A")) :: Nil =>
    }
    //TODO
//    assertProblems("(a: A*)", "(B, B)") {
//      case TypeMismatch(Expression("B"), Type("A")) :: TypeMismatch(Expression("B"), Type("A")) :: Nil =>
//    }
//    assertProblems("(a: A*)", "(A, B)") {
//      case TypeMismatch(Expression("B"), Type("A")) :: Nil =>
//    }
//    assertProblems("(a: A*)", "(B, A)") {
//      case Nil =>
//    }
//    assertProblems("(a: A*)", "(A, B, A)") {
//      case Nil =>
//    }
//    assertProblems("(a: A*)", "(A, B, B, A)") {
//      case Nil =>
//    }
  }

  def testExpansionToRepeated() {
    assertProblems("(a: A*)", "(Seq(A): _*)") {
      case Nil =>
    }
    assertProblems("(a: A, b: B*)", "(a, Seq(B): _*)") {
      case Nil =>
    }                               
  }
  
  def testExpansionToSingular() {
    assertProblems("(a: A)", "(Seq(A): _*)") {
      case ExpansionForNonRepeatedParameter(Expression("Seq(A): _*")) :: Nil =>
    }
    assertProblems("(a: A, b: B)", "(A, Seq(B): _*)") {
      case ExpansionForNonRepeatedParameter(Expression("Seq(B): _*")) :: Nil =>
    }
    assertProblems("(a: A, b: B)", "(Seq(A): _*, Seq(B): _*)") {
      case ExpansionForNonRepeatedParameter(Expression("Seq(A): _*")) :: 
              ExpansionForNonRepeatedParameter(Expression("Seq(B): _*")) :: Nil =>
    }
    assertProblems("(a: A, b: B*)", "(Seq(A): _*, B)") {
      case ExpansionForNonRepeatedParameter(Expression("Seq(A): _*")) :: Nil =>
    }
  }
  
  def testExpansionToSeq() {
    assertProblems("(a: Seq[A])", "(Seq(A): _*)") {
      case TypeMismatch(Expression("Seq(A): _*"), Type("Seq[Seq[A]]")) :: Nil =>
    }
  }
  
  def testSeqToRepeated() {
    assertProblems("(a: A*)", "(Seq(A))") {
      case TypeMismatch(Expression("Seq(A)"), Type("A")) :: Nil =>
    }
  }
  
  def testSeqToSingular() {
    assertProblems("(a: A)", "(Seq(A))") {
      case TypeMismatch(Expression("Seq(A)"), Type("A")) :: Nil =>
    }
  }
  
  def testSeqToSeq() {
    assertProblems("(a: Seq[A])", "(Seq(A))") {
      case Nil =>
    }
  }
  
  def testSinglularToSeq() {
    assertProblems("(a: Seq[A])", "(A)") {
      case TypeMismatch(Expression("A"), Type("Seq[A]")) :: Nil =>
    }
  }
  
  def testExpansionOfSingular() {
    assertProblems("(a: A*)", "(A: _*)") {
      case TypeMismatch(Expression("A: _*"), Type("Seq[A]")) :: Nil =>
    }
  }
  
  def testExpansionTypeMismatch() {
    assertProblems("(a: A*)", "(Seq(B): _*)") {
      case TypeMismatch(Expression("Seq(B): _*"), Type("Seq[A]")) :: Nil =>
    }
    assertProblems("(a: A, b: B*)", "(A, Seq(C): _*)") {
      case TypeMismatch(Expression("Seq(C): _*"), Type("Seq[B]")) :: Nil =>
    }
  }


  def testNamedWithRepeated(): Unit = {
    assertProblems("(a: A*)", "(a = Seq(A): _*)") {
      case Nil =>
    }

    assertProblems("(a: A)", "(a = Seq(A): _*)") {
      case ExpansionForNonRepeatedParameter(Expression("Seq(A): _*")) :: Nil =>
    }

    assertProblems("(a: A, b: B*)", "(A, b = Seq(A): _*)") {
      case TypeMismatch(Expression("Seq(A): _*"), Type("Seq[B]")) :: Nil =>
    }
  }
}