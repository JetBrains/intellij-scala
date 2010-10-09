package org.jetbrains.plugins.scala
package annotator.applicability

import lang.psi.types._

// See: SCL-2001
class PendingTuplingTest extends ApplicabilityTestBase {
  def testTuplingOkay {
    assertProblems("(a: Any)", "(A, B)") {
      case Nil =>
    }
    assertProblems("(a: AnyRef)", "(A, B, C)") {
      case Nil =>
    }
    assertProblems("(a: (A, B))", "(A, B, C)") {
      case Nil =>
    }
    assertProblems("[X, Y, Z](a: (X, Y, Z))", "(A, B, C)") {
      case Nil =>
    }
  }

  def testTuplingOkayTypeArgs {
    assertProblems("[X, Y, Z](a: (X, Y, Z))", "(A, B, C)") {
      case Nil =>
    }
  }

  def testTuplingTooMany {
    assertProblems("(a: (A, B))", "(A, B, C)") {
      case ExcessArgument(Expression("B")) :: ExcessArgument(Expression("C")) :: Nil =>
    }
  }
}