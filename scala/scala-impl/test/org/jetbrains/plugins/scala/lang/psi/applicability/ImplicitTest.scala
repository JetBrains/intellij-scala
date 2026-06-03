package org.jetbrains.plugins.scala
package lang.psi.applicability

import org.jetbrains.plugins.scala.lang.psi.types._

class ImplicitTest extends ApplicabilityTestBase {
  def testExplicitArguments(): Unit = {
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

  def testImplicitValue(): Unit = {
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

  def testEmptyArguments(): Unit = {
    assertProblemsConstructor("", "(implicit p: A)", "") {
      case Nil =>
    }
  }
  
  def testSwitchToExplicitMode(): Unit = {
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