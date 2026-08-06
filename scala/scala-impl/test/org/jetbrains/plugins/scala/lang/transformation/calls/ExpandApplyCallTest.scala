package org.jetbrains.plugins.scala
package lang
package transformation
package calls

class ExpandApplyCallTest extends TransformerTest(new ExpandApplyCall()) {

  override protected val header: String =
    """
     object O {
       def apply(p: A) {}
       def apply(p1: A, p2: A) {}
       def f(p: A) {}
     }
  """

  def testSingleArgument(): Unit = check(
    before = "O(A)",
    after = "O.apply(A)"
  )()

  def testMultipleArguments(): Unit = check(
    before = "O(A, A)",
    after = "O.apply(A, A)"
  )()

  def testSynthetic(): Unit = check(
    before = "S(A)",
    after = "S.apply(A)"
  )(header = "case class S(a: A)")

  def testExplicit(): Unit = check(
    before = "O.apply(A)",
    after = "O.apply(A)"
  )()

  def testOtherMethod(): Unit = check(
    before = "O.f(A)",
    after = "O.f(A)"
  )()

  def testIndirectResolution(): Unit = check(
    before = "v(A)",
    after = "v.apply(A)"
  )(header = "val v = O")

  def testCompoundQualifier(): Unit = check(
    before = "O1.O2(A)",
    after = "O1.O2.apply(A)"
  )(header =
    """
     object O1 {
       object O2 {
         def apply(p: A): Unit = {}
       }
     }
    """)

  // TODO test renamed "apply" method
}
