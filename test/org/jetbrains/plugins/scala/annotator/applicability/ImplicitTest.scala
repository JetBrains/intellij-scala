package org.jetbrains.plugins.scala
package annotator.applicability

import lang.psi.types._

/**
 * Pavel.Fatin, 18.05.2010
 */

class ImplicitTest extends ApplicabilityTestBase {
  def testExplicitArguments {
    assertProblems("(implicit p: A)", "(A)") {
      case Nil =>
    }
    assertProblems("(implicit a: A, b: B)", "(A, B)") {
      case Nil =>
    }
  }

  def testImplicitValue {
    assertProblems("implicit val v = A", "(implicit p: A)", "") {
      case Nil =>
    }
    assertProblems("implicit val v = A", "(implicit a: A, b: A)", "") {
      case Nil =>
    }
    assertProblems("implicit val a = A; implicit val b = B", "(implicit a: A, b: B)", "") {
      case Nil =>
    }
  }
  
  def testSwitchToExplicitMode {
    assertProblems("implicit val v = A", "(implicit p: A)", "()") {
      case MissedValueParameter(Parameter("p")) :: Nil =>
    }
    assertProblems("implicit val v = A", "(implicit a: A, b: B)", "()") {
      case MissedValueParameter(Parameter("a")) ::
              MissedValueParameter(Parameter("b")) :: Nil =>
    }
    assertProblems("implicit val v = B", "(implicit a: A, b: B)", "(A)") {
      case MissedValueParameter(Parameter("b")) :: Nil =>
    }
  }
}