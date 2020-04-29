package org.jetbrains.plugins.scala
package lang
package transformation
package calls

import org.junit.Ignore


/**
  * @author Pavel Fatin
  */
class CanonizeBlockArgumentTest extends TransformerTest(new CanonizeBlockArgument()) {
  def testMethodCall(): Unit = check(
    before = "f {A}",
    after = "f(A)"
  )()

  def testComplexExpression(): Unit = check(
    before = "f {A; B}",
    after = "f({A; B})"
  )()

  def testMultipleClauses(): Unit = check(
    before = "f(A) {B}",
    after = "f(A)(B)"
  )()

  def testExplicit(): Unit = check(
    before = "f(A)",
    after = "f(A)"
  )()

  // TODO test synthetic method
}

@Ignore("flaky tests")
class CanonizeBlockArgumentTestIgnored extends TransformerTest(new CanonizeBlockArgument()) {
  def testInfixExpression(): Unit = check(
    before = "O f {A}",
    after = "O f (A)"
  )()
}