package org.jetbrains.plugins.scala
package lang
package transformation
package calls

class ExpandUpdateCallTest extends TransformerTest(new ExpandUpdateCall()) {

  override protected val header: String =
    """
     object O {
       def update(k: A, v: B) {}
       def update(k1: A, k2: A, v: B) {}
       def f(k: A, v: B) {}
     }
    """

  def testSingleArgument(): Unit = check(
    before = "O(A) = B",
    after = "O.update(A, B)"
  )()

  def testMultipleArguments(): Unit = check(
    before = "O(A, A) = B",
    after = "O.update(A, A, B)"
  )()

  def testExplicit(): Unit = check(
    before = "O.update(A, B)",
    after = "O.update(A, B)"
  )()

  def testOtherMethod(): Unit = check(
    before = "O.f(A, B)",
    after = "O.f(A, B)"
  )()

  // TODO set ResolveResult.innerResolveResult as we do for "appply"
  //  def testIndirectResolution(): Unit = check(
  //    before = "v(A) = B",
  //    after = "v.update(A) = B"
  //  )(header = "val v = O")

  // TODO test renamed "update" method
}
