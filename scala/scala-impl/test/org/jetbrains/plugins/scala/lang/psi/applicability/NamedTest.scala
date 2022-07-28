package org.jetbrains.plugins.scala
package lang.psi.applicability

import org.jetbrains.plugins.scala.lang.psi.types._

class NamedTest extends ApplicabilityTestBase {
  def testFine(): Unit = {
    assertProblems("(a: A)", "(a = A)") {
      case Nil =>
    }
    assertProblems("(a: A, b: B)", "(a = A, b = B)") {
      case Nil =>
    }
  }

  def testReversed(): Unit = {
    assertProblems("(a: A, b: B)", "(b = B, a = A)") {
      case Nil =>
    }
  }

  def testPositionalWithNamed(): Unit = {
    assertProblems("(a: A, b: B)", "(A, b = B)") {
      case Nil =>
    }
    //TODO compiler allows such calls, they seem to be OK 
    //    assertProblems("(a: A, b: B)", "(a = A, b)") {
    //      case Nil =>
    //    }
  }

  def testPositionalAfterNamed(): Unit = {
    assertProblems("(a: A, b: B)", "(b = B, A)") {
      case PositionalAfterNamedArgument(Expression("A")) :: Nil =>
    }
    assertProblems("(a: A, b: B, c: C)", "(c = C, A, B)") {
      case PositionalAfterNamedArgument(Expression("A")) ::
              PositionalAfterNamedArgument(Expression("B")) :: Nil =>
    }
    assertProblems("(a: A, b: B, c: C)", "(c = C, A, B)") {
      case PositionalAfterNamedArgument(Expression("A")) ::
              PositionalAfterNamedArgument(Expression("B")) :: Nil =>
    }
    assertProblems("(a: A, b: B, c: C)", "(A, c = C, B)") {
      case PositionalAfterNamedArgument(Expression("B")) :: Nil =>
    }
  }

  def testNamedDuplicates(): Unit = {
    assertProblems("(a: A)", "(a = A, a = null)") {
      case ParameterSpecifiedMultipleTimes(Assignment("a = A")) ::
              ParameterSpecifiedMultipleTimes(Assignment("a = null")) :: Nil =>
    }
    assertProblems("(a: A)", "(a = A, a = A, a = A)") {
      case ParameterSpecifiedMultipleTimes(Assignment("a = A")) ::
              ParameterSpecifiedMultipleTimes(Assignment("a = A")) ::
              ParameterSpecifiedMultipleTimes(Assignment("a = A")) :: Nil =>
    }
    assertProblems("(a: A, b: B)", "(a = A, a = null, b = B, b = null)") {
      case ParameterSpecifiedMultipleTimes(Assignment("a = A")) ::
              ParameterSpecifiedMultipleTimes(Assignment("a = null")) ::
              ParameterSpecifiedMultipleTimes(Assignment("b = B")) ::
              ParameterSpecifiedMultipleTimes(Assignment("b = null")) :: Nil =>
    }
    assertProblems("(a: A, b: B)", "(A, b = B, b = null)") {
      case ParameterSpecifiedMultipleTimes(Assignment("b = B")) ::
              ParameterSpecifiedMultipleTimes(Assignment("b = null")) :: Nil =>
    }
  }
  
  def testUnresolvedParameter(): Unit = {
    assertProblems("()", "(a = A)") {
      case ExcessArgument(Assignment("a = A")) :: Nil =>
    }
    assertProblems("()", "(a = A, b = B)") {
      case ExcessArgument(Assignment("a = A")) ::
        ExcessArgument(Assignment("b = B")) :: Nil =>
    }
    assertProblems("(a: A)", "(a = A, b = B)") {
      case ExcessArgument(Assignment("b = B")) :: Nil =>
    }
  }
  
  def testNamedUnresolvedDuplicates(): Unit = {
    assertProblems("(a: A)", "(b = A, b = null)") {
      case ParameterSpecifiedMultipleTimes(Assignment("b = A")) ::
              ParameterSpecifiedMultipleTimes(Assignment("b = null")) :: Nil =>
    }
  }

  /*def testDoesNotTakeParameters {
    assertProblems("", "(a = A)") {
      case DoesNotTakeParameters() :: Nil =>
    }
    assertProblems("", "(a = A, b = B)") {
      case DoesNotTakeParameters() :: Nil =>
    }
  }*/
  
  def testTooManyArguments(): Unit = {
    assertProblems("(a: A)", "(A, a = A)") {
      case ExcessArgument(Expression("a = A")) :: Nil =>
    }
    assertProblems("(a: A, b: B)", "(A, B, a = A)") {
      case ExcessArgument(Expression("a = A")) :: Nil =>
    }
    assertProblems("(a: A, b: B)", "(A, B, b = B)") {
      case ExcessArgument(Expression("b = B")) :: Nil =>
    }
    assertProblems("(a: A, b: B)", "(A, B, a = A, b = B)") {
      case ExcessArgument(Expression("a = A")) :: 
              ExcessArgument(Expression("b = B")) :: Nil =>
    }
  }  
  
  def testTypeMismatch(): Unit = {
    assertProblems("(a: A)", "(a = B)") {
      case TypeMismatch(Expression("B"), Type("A")) :: Nil =>
    }
    assertProblems("(a: A, b: B)", "(a = B, b = A)") {
      case TypeMismatch(Expression("B"), Type("A")) :: TypeMismatch(Expression("A"), Type("B")) :: Nil =>
    }
  }
}