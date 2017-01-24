package org.jetbrains.plugins.scala
package lang
package transformation
package calls

/**
  * @author Pavel Fatin
  */
class CanonizeZeroArityCallTest extends TransformerTest(new CanonizeZeroArityCall()) {

  override protected val header: String =
    """
     object O {
       def f(): A = _
       def g: A = _
     }
  """

  def testImplicit(): Unit = check(
    before = "O.f",
    after = "O.f()"
  )()

  def testInapplicable(): Unit = check(
    before = "O.g",
    after = "O.g"
  )()

  def testExplicit(): Unit = check(
    before = "O.f()",
    after = "O.f()"
  )()
}