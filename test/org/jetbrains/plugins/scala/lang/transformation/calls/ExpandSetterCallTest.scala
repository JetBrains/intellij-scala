package org.jetbrains.plugins.scala.lang.transformation.calls

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class ExpandSetterCallTest extends TransformerTest(new ExpandSetterCall(),
  """
     object O {
       var v1: A = _
       var v2: A
       private[this] var v3: A = _
     }
  """) {

  def testPropertyDefinition() = check(
    "O.v1 = A",
    "O.v1_=(A)"
  )

  def testPropertyDeclaration() = check(
    "O.v2 = A",
    "O.v2_=(A)"
  )

  def testPrivateThisVariable() = check(
    "O.v3 = A",
    "O.v3 = A"
  )

  def testIncomplete() = check(
    "O.v1 =",
    "O.v1_=()"
  )

  def testBlockVariable() = check(
    """
     {
       val v: A = _
    """,
    "v = A",
    "v = A",
    "}"
  )

  def testExplicitCall() = check(
    "O.v_=(A)",
    "O.v_=(A)"
  )

  // TODO variable in compiled code
}