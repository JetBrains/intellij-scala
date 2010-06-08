package org.jetbrains.plugins.scala
package annotator.applicability

import lang.psi.types._
/**
 * Pavel.Fatin, 18.05.2010
 */

class BasicTest extends Base {
  def testEmpty {
    assertMatches(problems("")) {
      case Nil =>
    }
  }

  def testFine {
    assertMatches(problems("def f {}; f")) {
      case Nil =>
    }
    assertMatches(problems("def f() {}; f()")) {
      case Nil =>
    }
    assertMatches(problems("def f() {}; f")) {
      case Nil =>
    }
    assertMatches(problems("def f(p: A) {}; f(A)")) {
      case Nil =>
    }
    assertMatches(problems("def f(a: A, b: B) {}; f(A, B)")) {
      case Nil =>
    }
    assertMatches(problems("def f(a: A)(b: B) {}; f(A)(B)")) {
      case Nil =>
    }
  }

  def testDoesNotTakeParameters {
    assertMatches(problems("def f {}; f()")) {
      case DoesNotTakeParameters() :: Nil =>
    }
    assertMatches(problems("def f {}; f(A)")) {
      case DoesNotTakeParameters() :: Nil =>
    }
    assertMatches(problems("def f {}; f(A, B)")) {
      case DoesNotTakeParameters() :: Nil =>
    }
    assertMatches(problems("def f {}; f(A)(B)")) {
      case DoesNotTakeParameters() :: Nil =>
    }
  }

  def testTooManyArguments {
    assertMatches(problems("def f() {}; f(A)")) {
      case ExcessArgument(Expression("A")) :: Nil =>
    }
    assertMatches(problems("def f() {}; f(A, B)")) {
      case ExcessArgument(Expression("A")) :: ExcessArgument(Expression("B")) :: Nil =>
    }
    assertMatches(problems("def f(p: A) {}; f(A, B)")) {
      case ExcessArgument(Expression("B")) :: Nil =>
    }
    assertMatches(problems("def f(a: A, b: B) {}; f(A, B, C)")) {
      case ExcessArgument(Expression("C")) :: Nil =>
    }
  }

  //TODO check misses clauses extraction
  def testMissedParametersClause {
    assertMatches(problems("def f(p: A) {}; f")) {
      case MissedParametersClause(_) :: Nil =>
    }
    assertMatches(problems("def f(a: A, b: B) {}; f")) {
      case MissedParametersClause(_) :: Nil =>
    }
    assertMatches(problems("def f(a: A)(b: B) {}; f")) {
      case MissedParametersClause(_) :: Nil =>
    }
  }

  def testMissedParameter {
    assertMatches(problems("def f(a: A) {}; f()")) {
      case MissedParameter(Named("a")) :: Nil =>
    }
    assertMatches(problems("def f(a: A, b: B) {}; f(A)")) {
      case MissedParameter(Named("b")) :: Nil =>
    }
    assertMatches(problems("def f(a: A, b: B) {}; f()")) {
      case MissedParameter(Named("a")) :: MissedParameter(Named("b")) :: Nil =>
    }
  }
  
  def testTypeMismatch {
    assertMatches(problems("def f(a: A) {}; f(B)")) {
      case TypeMismatch(Expression("B"), Type("A")) :: Nil =>
    }
    assertMatches(problems("def f(a: A, b: B) {}; f(B, A)")) {
      case TypeMismatch(Expression("B"), Type("A")) :: TypeMismatch(Expression("A"), Type("B")) :: Nil =>
    }
  }
}