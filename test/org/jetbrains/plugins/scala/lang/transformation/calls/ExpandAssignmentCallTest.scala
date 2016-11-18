package org.jetbrains.plugins.scala.lang.transformation.calls

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class ExpandAssignmentCallTest extends TransformerTest(new ExpandAssignmentCall(),
  """
     class T {
       def +(p: A) = new T()
       def ::(p: A) = new T()
     }

     var t = new T()
  """) {

  def testImplicit() = check(
    "t += A",
    "t = t + A"
  )

  def testSynthetic() = check(
    "var i = 0",
    "i += 1",
    "i = i + 1"
  )

  def testRightAssociative() = check(
    "t ::= A",
    "t = A :: t"
  )

  def testExplicit() = check(
    "t = t + A",
    "t = t + A"
  )

  def testWithoutAssignment() = check(
    "t + A",
    "t + A"
  )

  def testRealMethod() = check(
    """
     class R {
       def +=(p: A) = new R()
     }

     var r = new R()
    """,
    "r += A",
    "r += A"
  )
}
