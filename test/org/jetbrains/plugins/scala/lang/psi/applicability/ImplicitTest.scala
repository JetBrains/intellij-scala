package org.jetbrains.plugins.scala
package lang.psi.applicability

import org.jetbrains.plugins.scala.lang.psi.types._

/**
 * Pavel.Fatin, 18.05.2010
 */

class ImplicitTest extends ApplicabilityTestBase {
  def testExplicitArguments() {
    assertProblemsFunction("", "(implicit p: A)", "(A)") {
      case Nil =>
    }
    assertProblemsConstructor("", "()(implicit p: A)", "()(A)") {
      case Nil =>
    }
    assertProblemsFunction("", "(implicit a: A, b: B)", "(A, B)") {
      case Nil =>
    }
  }

  def testImplicitValue() {
    assertProblemsFunction("implicit val v = A", "(implicit p: A)", "") {
      case Nil =>
    }
    assertProblemsFunction("implicit val v = A", "(implicit a: A, b: A)", "") {
      case Nil =>
    }
    assertProblemsFunction("implicit val a = A; implicit val b = B", "(implicit a: A, b: B)", "") {
      case Nil =>
    }
  }

  def testEmptyArguments() {
    assertProblemsConstructor("", "(implicit p: A)", "") {
      case Nil =>
    }
  }
  
  def testSwitchToExplicitMode() {
    assertProblemsFunction("implicit val v = A", "(implicit p: A)", "()") {
      case MissedValueParameter(Parameter("p")) :: Nil =>
    }
    assertProblemsFunction("implicit val v = A", "(implicit a: A, b: B)", "()") {
      case MissedValueParameter(Parameter("a")) ::
              MissedValueParameter(Parameter("b")) :: Nil =>
    }
    assertProblemsFunction("implicit val v = B", "(implicit a: A, b: B)", "(A)") {
      case MissedValueParameter(Parameter("b")) :: Nil =>
    }
  }
}