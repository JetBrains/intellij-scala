package org.jetbrains.plugins.scala
package annotator.applicability

import lang.psi.types._
/**
 * Pavel.Fatin, 18.05.2010
 */

class BasicTest extends Applicability {
  def testFine {
    assertProblems("", "") {
      case Nil =>
    }
    assertProblems("()", "()") {
      case Nil =>
    }
    assertProblems("()", "") {
      case Nil =>
    }
    assertProblems("(p: A)", "(A)") {
      case Nil =>
    }
    assertProblems("(a: A, b: B)", "(A, B)") {
      case Nil =>
    }
    assertProblems("(a: A)(b: B)", "(A)(B)") {
      case Nil =>
    }
  }

  def testDoesNotTakeParameters {
    assertProblems("", "()") {
      case DoesNotTakeParameters() :: Nil =>
    }
    assertProblems("", "(A)") {
      case DoesNotTakeParameters() :: Nil =>
    }
    assertProblems("", "(A, B)") {
      case DoesNotTakeParameters() :: Nil =>
    }
    assertProblems("", "(A)(B)") {
      case DoesNotTakeParameters() :: Nil =>
    }
  }

  def testTooManyArguments {
    assertProblems("()", "(A)") {
      case ExcessArgument(Expression("A")) :: Nil =>
    }
    assertProblems("()", "(A, B)") {
      case ExcessArgument(Expression("A")) :: ExcessArgument(Expression("B")) :: Nil =>
    }
    assertProblems("(p: A)", "(A, B)") {
      case ExcessArgument(Expression("B")) :: Nil =>
    }
    assertProblems("(a: A, b: B)", "(A, B, C)") {
      case ExcessArgument(Expression("C")) :: Nil =>
    }
  }

  //TODO check misses clauses extraction
  def testMissedParametersClause {
    assertProblems("(p: A)", "") {
      case MissedParametersClause(_) :: Nil =>
    }
    assertProblems("(a: A, b: B)", "") {
      case MissedParametersClause(_) :: Nil =>
    }
    assertProblems("(a: A)(b: B)", "") {
      case MissedParametersClause(_) :: Nil =>
    }
  }

  def testMissedParameter {
    assertProblems("(a: A)", "()") {
      case MissedParameter(Parameter("a")) :: Nil =>
    }
    assertProblems("(a: A, b: B)", "(A)") {
      case MissedParameter(Parameter("b")) :: Nil =>
    }
    assertProblems("(a: A, b: B)", "()") {
      case MissedParameter(Parameter("a")) :: MissedParameter(Parameter("b")) :: Nil =>
    }
  }
  
  def testTypeMismatch {
    assertProblems("(a: A)", "(B)") {
      case TypeMismatch(Expression("B"), Type("A")) :: Nil =>
    }
    assertProblems("(a: A, b: B)", "(B, A)") {
      case TypeMismatch(Expression("B"), Type("A")) :: TypeMismatch(Expression("A"), Type("B")) :: Nil =>
    }
  }
}