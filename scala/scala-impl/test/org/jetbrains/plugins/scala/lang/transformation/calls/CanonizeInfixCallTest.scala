package org.jetbrains.plugins.scala
package lang
package transformation
package calls

/**
  * @author Pavel Fatin
  */
class CanonizeInfixCallTest extends TransformerTest(new CanonizeInfixCall()) {

  override protected val header: String =
    """
     object O {
       def f(p: A) {}
       def ::(p: A) {}
     }
  """

  def testLeftAssociative(): Unit = check(
    before = "O f A",
    after = "O.f(A)"
  )()

  def testLeftAssociativeBlock(): Unit = check(
    before = "O f {A}",
    after = "O.f {A}"
  )()

  def testRightAssociative(): Unit = check(
    before = "A :: O",
    after = "O.::(A)"
  )()

  def testRightAssociativeBlock(): Unit = check(
    before = "{A} :: O",
    after = "O.:: {A}"
  )()

  def testMethodCall(): Unit = check(
    before = "O.f(A)",
    after = "O.f(A)"
  )()

  def testTwoInfix(): Unit = check(
    before = "(3 + 4) + 5",
    after = "(3 + 4).+(5)"
  )()
}
