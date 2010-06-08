package org.jetbrains.plugins.scala
package annotator.applicability

import lang.psi.types._
/**
 * Pavel.Fatin, 18.05.2010
 */

class DefaultTest extends Base {
  def testFine {
    assertMatches(problems("def f(a: A = null) {}; f()")) {
      case Nil =>
    }
    assertMatches(problems("def f(a: A = null) {}; f(A)")) {
      case Nil =>
    }
    assertMatches(problems("def f(a: A = null, b: B = null) {}; f()")) {
      case Nil =>
    }
    assertMatches(problems("def f(a: A = null, b: B = null) {}; f(A)")) {
      case Nil =>
    }
    assertMatches(problems("def f(a: A = null, b: B = null) {}; f(A, B)")) {
      case Nil =>
    }
  }
  
  def testMix {
    assertMatches(problems("def f(a: A, b: B = null) {}; f(A)")) {
      case Nil =>
    }
    assertMatches(problems("def f(a: A, b: B = null) {}; f(A, B)")) {
      case Nil =>
    }
    assertMatches(problems("def f(a: A = null, b: B) {}; f(A, B)")) {
      case Nil =>
    }
  }
  
  def testTooManyArguments {
    assertMatches(problems("def f(a: A = null) {}; f(A, B)")) {
      case ExcessArgument(Expression("B")) :: Nil =>
    }
    assertMatches(problems("def f(a: A = null) {}; f(A, B, C)")) {
      case ExcessArgument(Expression("B")) :: ExcessArgument(Expression("C")) ::Nil =>
    }
    assertMatches(problems("def f(a: A = null, b: B = null) {}; f(A, B, C)")) {
      case ExcessArgument(Expression("C")) :: Nil =>
    }
  }

  def testMissedParametersClause {
    assertMatches(problems("def f(p: A = null) {}; f")) {
      case MissedParametersClause(_) :: Nil =>
    }
  }
  
  def testMissedParameter {
    assertMatches(problems("def f(a: A, b: B = null) {}; f()")) {
      case MissedParameter(Named("a")) :: Nil =>
    }
    assertMatches(problems("def f(a: A, b: B = null, c: C = null) {}; f()")) {
      case MissedParameter(Named("a")) :: Nil =>
    }
    assertMatches(problems("def f(a: A, b: B, c: C = null) {}; f()")) {
      case MissedParameter(Named("a")) :: MissedParameter(Named("b")) ::Nil =>
    }
    assertMatches(problems("def f(a: A = null, b: B) {}; f()")) {
      case MissedParameter(Named("b")) :: Nil =>
    }
    assertMatches(problems("def f(a: A = null, b: B) {}; f(A)")) {
      case MissedParameter(Named("b")) :: Nil =>
    }
    assertMatches(problems("def f(a: A = null, b: B = null, c: C) {}; f()")) {
      case MissedParameter(Named("c")) :: Nil =>
    }
    assertMatches(problems("def f(a: A = null, b: B = null, c: C) {}; f(A)")) {
      case MissedParameter(Named("c")) :: Nil =>
    }
    assertMatches(problems("def f(a: A = null, b: B = null, c: C) {}; f(A, B)")) {
      case MissedParameter(Named("c")) :: Nil =>
    }
  }
  
  def testTypeMismatch {
    assertMatches(problems("def f(a: A = null) {}; f(B)")) {
      case TypeMismatch(Expression("B"), Type("A")) :: Nil =>
    }
    assertMatches(problems("def f(a: A = null, b: B = null) {}; f(B, A)")) {
      case TypeMismatch(Expression("B"), Type("A")) :: TypeMismatch(Expression("A"), Type("B")) :: Nil =>
    }
  }
}