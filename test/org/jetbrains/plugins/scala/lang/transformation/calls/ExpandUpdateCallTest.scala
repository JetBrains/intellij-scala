package org.jetbrains.plugins.scala.lang.transformation.calls

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class ExpandUpdateCallTest extends TransformerTest(new ExpandUpdateCall(),
  """
     object O {
       def update(k: A, v: B) {}
       def update(k1: A, k2: A, v: B) {}
       def f(k: A, v: B) {}
     }
    """) {

  def testSingleArgument() = check(
    "O(A) = B",
    "O.update(A, B)"
  )

  def testMultipleArguments() = check(
    "O(A, A) = B",
    "O.update(A, A, B)"
  )

  def testExplicit() = check(
    "O.update(A, B)",
    "O.update(A, B)"
  )

  def testOtherMethod() = check(
    "O.f(A, B)",
    "O.f(A, B)"
  )

// TODO set ResolveResult.innerResolveResult as we do for "appply"
//  def testIndirectResolution() = check(
//    "val v = O",
//    "v(A) = B",
//    "v.update(A) = B"
//  )

  // TODO test renamed "update" method
}
