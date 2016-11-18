package org.jetbrains.plugins.scala.lang.transformation.types

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class ExpandTupleTypeTest extends TransformerTest(new ExpandTupleType()) {
  def testTuple2() = check(
    "val v: (A, B)",
    "val v: Tuple2[A, B]"
  )

  def testTuple3() = check(
    "val v: (A, B, C)",
    "val v: Tuple3[A, B, C]"
  )

  def testParens() = check(
    "val v: (A)",
    "val v: (A)"
  )

  def testInsideFunctionType() = check(
    "val v: (A, B) => C",
    "val v: (A, B) => C"
  )

  def testExplicit() = check(
    "val v: Tuple2[A, B]",
    "val v: Tuple2[A, B]"
  )
}
