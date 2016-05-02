package org.jetbrains.plugins.scala.lang.transformation.calls

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class CanonizePostfixCallTest extends TransformerTest(CanonizePostifxCall,
  """
     object O {
       def f: A = _
     }
  """) {

  def testImplicit() = check(
    "O f",
    "O.f"
  )

  def testExplicit() = check(
    "O.f",
    "O.f"
  )
}
