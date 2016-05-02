package org.jetbrains.plugins.scala.lang.transformation.general

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class ExpandTupleInstantiationTest extends TransformerTest(ExpandTupleInstantiation) {
  def testTuple2() = check(
    "(A, B)",
    "Tuple2(A, B)"
  )

  def testTuple3() = check(
    "(A, B, C)",
    "Tuple3(A, B, C)"
  )

  def testExplicit() = check(
    "Tuple2(A, B)",
    "Tuple2(A, B)"
  )
}
