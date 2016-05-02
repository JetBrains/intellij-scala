package org.jetbrains.plugins.scala.lang.transformation.calls

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class ExpandApplyCallTest extends TransformerTest(ExpandApplyCall,
  """
     object O {
       def apply(p: A) {}
       def apply(p1: A, p2: A) {}
       def f(p: A) {}
     }
  """) {

  def testSingleArgument() = check(
    "O(A)",
    "O.apply(A)"
  )

  def testMultipleArguments() = check(
    "O(A, A)",
    "O.apply(A, A)"
  )

  def testSynthetic() = check(
    "case class S(a: A)",
    "S(A)",
    "S.apply(A)"
  )

  def testExplicit() =  check(
    "O.apply(A)",
    "O.apply(A)"
  )

  def testOtherMethod() = check(
    "O.f(A)",
    "O.f(A)"
  )

  def testIndirectResolution() = check(
    "val v = O",
    "v(A)",
    "v.apply(A)"
  )

  def testCompoundQualifier() = check(
    """
     object O1 {
       object O2 {
         def apply(p: A) {}
       }
     }
    """,
    "O1.O2(A)",
    "O1.O2.apply(A)"
  )

  // TODO test renamed "apply" method
}
