package org.jetbrains.plugins.scala.lang.transformation.calls

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class CanonizeZeroArityCallTest extends TransformerTest(new CanonizeZeroArityCall(),
  """
     object O {
       def f(): A = _
       def g: A = _
     }
  """) {

  def testImplicit() = check(
    "O.f",
    "O.f()"
  )

  def testInapplicable() = check(
    "O.g",
    "O.g"
  )

  def testExplicit() = check(
    "O.f()",
    "O.f()"
  )
}