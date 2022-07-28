package org.jetbrains.plugins.scala
package lang.psi.applicability

import org.jetbrains.plugins.scala.lang.psi.types._

class DefaultTest extends ApplicabilityTestBase {
  def testFine(): Unit = {
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
  
  def testMix(): Unit = {
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
  
  def testTooManyArguments(): Unit = {
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

  def testMissedParametersClause(): Unit = {
    assertProblemsFunction("", "(p: A = null)", "") {
      case MissedParametersClause(_) :: Nil =>
    }
    // This is allowed.
    assertProblemsConstructor("", "(p: A = null)", "") {
      case Nil =>
    }
  }
  
  def testMissedParameter(): Unit = {
    assertProblems("(a: A, b: B = null)", "()") {
      case MissedValueParameter(Parameter("a")) :: Nil =>
    }
    assertProblems("(a: A, b: B = null, c: C = null)", "()") {
      case MissedValueParameter(Parameter("a")) :: Nil =>
    }
    assertProblems("(a: A, b: B, c: C = null)", "()") {
      case MissedValueParameter(Parameter("a")) :: MissedValueParameter(Parameter("b")) ::Nil =>
    }
    assertProblems("(a: A = null, b: B)", "()") {
      case MissedValueParameter(Parameter("b")) :: Nil =>
    }
    assertProblems("(a: A = null, b: B)", "(A)") {
      case MissedValueParameter(Parameter("b")) :: Nil =>
    }
    assertProblems("(a: A = null, b: B = null, c: C)", "()") {
      case MissedValueParameter(Parameter("c")) :: Nil =>
    }
    assertProblems("(a: A = null, b: B = null, c: C)", "(A)") {
      case MissedValueParameter(Parameter("c")) :: Nil =>
    }
    assertProblems("(a: A = null, b: B = null, c: C)", "(A, B)") {
      case MissedValueParameter(Parameter("c")) :: Nil =>
    }
  }
  
  def testTypeMismatch(): Unit = {
    assertProblems("(a: A = null)", "(B)") {
      case TypeMismatch(Expression("B"), Type("A")) :: Nil =>
    }
    assertProblems("(a: A = null, b: B = null)", "(B, A)") {
      case TypeMismatch(Expression("B"), Type("A")) :: TypeMismatch(Expression("A"), Type("B")) :: Nil =>
    }
  }
}