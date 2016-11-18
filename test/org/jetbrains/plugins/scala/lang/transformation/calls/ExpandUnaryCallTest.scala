package org.jetbrains.plugins.scala.lang.transformation.calls

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class ExpandUnaryCallTest extends TransformerTest(new ExpandUnaryCall(),
  """
     object O {
       def unary_! {}
       def !(p: A) {}
     }
  """) {

  def testImplicit() = check(
    "!O",
    "O.unary_!"
  )

  def testSynthetic() = check(
    "!true",
    "true.unary_!"
  )

  def testExplicit() =  check(
    "O.unary_!",
    "O.unary_!"
  )

  def testOtherPrefix() = check(
    "+O",
    "+O"
  )

  def testOtherMethod() = check(
    "O.!(A)",
    "O.!(A)"
  )

  // TODO test renamed method
}
