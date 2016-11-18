package org.jetbrains.plugins.scala.lang.transformation.calls

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class CanonizeInfixCallTest extends TransformerTest(new CanonizeInfixCall(),
  """
     object O {
       def f(p: A) {}
       def ::(p: A) {}
     }
  """) {

  def testLeftAssociative() = check(
    "O f A",
    "O.f(A)"
  )

  def testLeftAssociativeBlock() = check(
    "O f {A}",
    "O.f {A}"
  )

  def testRigthAssociative() = check(
    "A :: O",
    "O.::(A)"
  )

  def testRightAssociativeBlock() = check(
    "{A} :: O",
    "O.:: {A}"
  )

  def testMethodCall() = check(
    "O.f(A)",
    "O.f(A)"
  )
}
