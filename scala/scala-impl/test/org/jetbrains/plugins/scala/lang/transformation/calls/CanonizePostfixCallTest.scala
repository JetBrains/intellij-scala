package org.jetbrains.plugins.scala
package lang
package transformation
package calls

class CanonizePostfixCallTest extends TransformerTest(new CanonizePostifxCall()) {

  override protected val header: String =
    """
     object O {
       def f: A = _
     }
  """

  def testImplicit(): Unit = check(
    before = "O f",
    after = "O.f"
  )()

  def testExplicit(): Unit = check(
    before = "O.f",
    after = "O.f"
  )()
}
