package org.jetbrains.plugins.scala
package lang.psi.applicability

import org.jetbrains.plugins.scala.lang.psi.types._

class BasicTest extends ApplicabilityTestBase {
  def testFine(): Unit = {
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

  /*def testDoesNotTakeParameters {
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
  }*/

  def testTooManyArguments(): Unit = {
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

  def testMissedParametersClause(): Unit = {
    //for functions and for constructors there are different message
    //reason: you can't apply eta-expansion for constructors
    assertProblems("(p: A)", "") {
      case MissedParametersClause(_) :: Nil =>
      case MissedValueParameter(Parameter("p")) :: Nil =>
    }
    assertProblems("(a: A, b: B)", "") {
      case MissedParametersClause(_) :: Nil =>
      case MissedValueParameter(Parameter("a")) :: MissedValueParameter(Parameter("b")) :: Nil =>
    }
    assertProblems("(a: A)(b: B)", "") {
      case MissedParametersClause(_) :: Nil =>
      case MissedValueParameter(Parameter("a")) :: Nil =>
    }
  }

  def testMissedParameter(): Unit = {
    assertProblems("(a: A)", "()") {
      case MissedValueParameter(Parameter("a")) :: Nil =>
    }
    assertProblems("(a: A, b: B)", "(A)") {
      case MissedValueParameter(Parameter("b")) :: Nil =>
    }
    assertProblems("(a: A, b: B)", "()") {
      case MissedValueParameter(Parameter("a")) :: MissedValueParameter(Parameter("b")) :: Nil =>
    }
  }
  
  def testTypeMismatch(): Unit = {
    assertProblems("(a: A)", "(B)") {
      case TypeMismatch(Expression("B"), Type("A")) :: Nil =>
    }
    assertProblems("(a: A, b: B)", "(B, A)") {
      case TypeMismatch(Expression("B"), Type("A")) :: TypeMismatch(Expression("A"), Type("B")) :: Nil =>
    }
  }
}