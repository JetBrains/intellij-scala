package org.jetbrains.plugins.scala.lang.transformation.calls

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class ExpandAutoTuplingTest extends TransformerTest(new ExpandAutoTupling(),
  """
     object O {
       def f(v: Any) {}
     }
  """) {

  def testMethodCall() = check(
    "O.f(A, B)",
    "O.f((A, B))"
  )

  def testInfixExpression() = check(
    "O f (A, B)",
    "O f ((A, B))"
  )

  def testSingleArgument() = check(
    "O.f(A)",
    "O.f(A)"
  )

  def testExplicit() = check(
    "O.f((A, B))",
    "O.f((A, B))"
  )
}