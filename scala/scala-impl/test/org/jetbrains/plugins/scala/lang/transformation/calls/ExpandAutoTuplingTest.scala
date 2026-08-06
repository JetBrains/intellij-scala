package org.jetbrains.plugins.scala
package lang
package transformation
package calls

class ExpandAutoTuplingTest extends TransformerTest(new ExpandAutoTupling()) {

  override protected val header: String =
    """
     object O {
       def f(v: Any) {}
     }
  """

  def testMethodCall(): Unit = check(
    before = "O.f(A, B)",
    after = "O.f((A, B))"
  )()

  def testInfixExpression(): Unit = check(
    before = "O f (A, B)",
    after = "O f ((A, B))"
  )()

  def testSingleArgument(): Unit = check(
    before = "O.f(A)",
    after = "O.f(A)"
  )()

  def testExplicit(): Unit = check(
    before = "O.f((A, B))",
    after = "O.f((A, B))"
  )()
}