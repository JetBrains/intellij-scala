package org.jetbrains.plugins.scala
package annotator.applicability

import lang.psi.types._
/**
 * Pavel.Fatin, 18.05.2010
 */

abstract class Default extends Applicability {
  def testFine {
    assertProblems("(a: A = null)", "()") {
      case Nil =>
    }
    assertProblems("(a: A = null)", "(A)") {
      case Nil =>
    }
    assertProblems("(a: A = null, b: B = null)", "()") {
      case Nil =>
    }
    assertProblems("(a: A = null, b: B = null)", "(A)") {
      case Nil =>
    }
    assertProblems("(a: A = null, b: B = null)", "(A, B)") {
      case Nil =>
    }
  }
  
  def testMix {
    assertProblems("(a: A, b: B = null)", "(A)") {
      case Nil =>
    }
    assertProblems("(a: A, b: B = null)", "(A, B)") {
      case Nil =>
    }
    assertProblems("(a: A = null, b: B)", "(A, B)") {
      case Nil =>
    }
  }
  
  def testTooManyArguments {
    assertProblems("(a: A = null)", "(A, B)") {
      case ExcessArgument(Expression("B")) :: Nil =>
    }
    assertProblems("(a: A = null)", "(A, B, C)") {
      case ExcessArgument(Expression("B")) :: ExcessArgument(Expression("C")) ::Nil =>
    }
    assertProblems("(a: A = null, b: B = null)", "(A, B, C)") {
      case ExcessArgument(Expression("C")) :: Nil =>
    }
  }

  def testMissedParametersClause {
    assertProblems("(p: A = null)", "") {
      case MissedParametersClause(_) :: Nil =>
    }
  }
  
  def testMissedParameter {
    assertProblems("(a: A, b: B = null)", "()") {
      case MissedParameter(Named("a")) :: Nil =>
    }
    assertProblems("(a: A, b: B = null, c: C = null)", "()") {
      case MissedParameter(Named("a")) :: Nil =>
    }
    assertProblems("(a: A, b: B, c: C = null)", "()") {
      case MissedParameter(Named("a")) :: MissedParameter(Named("b")) ::Nil =>
    }
    assertProblems("(a: A = null, b: B)", "()") {
      case MissedParameter(Named("b")) :: Nil =>
    }
    assertProblems("(a: A = null, b: B)", "(A)") {
      case MissedParameter(Named("b")) :: Nil =>
    }
    assertProblems("(a: A = null, b: B = null, c: C)", "()") {
      case MissedParameter(Named("c")) :: Nil =>
    }
    assertProblems("(a: A = null, b: B = null, c: C)", "(A)") {
      case MissedParameter(Named("c")) :: Nil =>
    }
    assertProblems("(a: A = null, b: B = null, c: C)", "(A, B)") {
      case MissedParameter(Named("c")) :: Nil =>
    }
  }
  
  def testTypeMismatch {
    assertProblems("(a: A = null)", "(B)") {
      case TypeMismatch(Expression("B"), Type("A")) :: Nil =>
    }
    assertProblems("(a: A = null, b: B = null)", "(B, A)") {
      case TypeMismatch(Expression("B"), Type("A")) :: TypeMismatch(Expression("A"), Type("B")) :: Nil =>
    }
  }
}