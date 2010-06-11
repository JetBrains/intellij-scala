package org.jetbrains.plugins.scala
package annotator.applicability

import lang.psi.types._
import lang.psi.api.toplevel.typedef.ScTrait

/**
 * Pavel.Fatin, 18.05.2010
 */

abstract class Repeated extends Applicability {
  def testMalformedDefinition {
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
  
  def testMalformedDefinitionClauses {
    assertProblems("(a: A)(b: B*, c: C)", "(A)(B, C)") {
      case MalformedDefinition() :: Nil =>
    }
  }
  
  def testValidDefinition {
    assertProblems("(a: A*)(b: B)", "(A)(B)") {
      case Nil =>
    }
  }
  
  def testFineSingle {
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
  
  def testFineSecond {
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
  
  def testFineSecondSameType {
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
  
  def testFineThird {
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
  
  def testMissedArguments {
    assertProblems("(a: A*)", "") {
      case MissedParametersClause(_) :: Nil =>
    }
  }
  
  def testTypeMismatch {
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

  def testExpansion {
    assertProblems("(a: A*)", "(Seq(A): _*)") {
      case Nil =>
    }
    assertProblems("(a: A, b: B*)", "(a, Seq(B): _*)") {
      case Nil =>
    }
  } 
  
  // not last expansion, twice
  
  def testExpansionToNonRepeated {
    assertProblems("(a: A)", "(Seq(A): _*)") {
      case TypeMismatch(Expression("Seq(A): _*"), Type("A")) :: Nil =>
    }
    assertProblems("(a: A, b: B)", "(A, Seq(B): _*)") {
      case TypeMismatch(Expression("Seq(B): _*"), Type("B")) :: Nil =>
    }
  }
  
  def testExpansionTypeMismatch {
    assertProblems("(a: A*)", "(Seq(B): _*)") {
      case TypeMismatch(Expression("Seq(B): _*"), Type("Seq[A]")) :: Nil =>
    }
    assertProblems("(a: A, b: B*)", "(A, Seq(C): _*)") {
      case TypeMismatch(Expression("Seq(C): _*"), Type("Seq[B]")) :: Nil =>
    }
  }  
}