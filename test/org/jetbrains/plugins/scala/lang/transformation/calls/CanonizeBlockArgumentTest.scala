package org.jetbrains.plugins.scala.lang.transformation.calls

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class CanonizeBlockArgumentTest extends TransformerTest(new CanonizeBlockArgument()) {
  def testMethodCall() = check(
    "f {A}",
    "f(A)"
  )

  def testInfixExpression() = check(
    "O f {A}",
    "O f (A)"
  )

  def testComplexExpression() = check(
    "f {A; B}",
    "f({A; B})"
  )

  def testMultipleClauses() = check(
    "f(A) {B}",
    "f(A)(B)"
  )

  def testExplicit() = check(
    "f(A)",
    "f(A)"
  )

  // TODO test synthetic method
}
