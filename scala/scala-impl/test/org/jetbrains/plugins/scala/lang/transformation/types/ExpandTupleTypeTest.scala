package org.jetbrains.plugins.scala
package lang
package transformation
package types

class ExpandTupleTypeTest extends TransformerTest(new ExpandTupleType()) {

  def testTuple2(): Unit = check(
    before = "val v: (A, B)",
    after = "val v: Tuple2[A, B]"
  )()

  def testTuple3(): Unit = check(
    before = "val v: (A, B, C)",
    after = "val v: Tuple3[A, B, C]"
  )()

  def testParenthesis(): Unit = check(
    before = "val v: (A)",
    after = "val v: (A)"
  )()

  def testInsideFunctionType(): Unit = check(
    before = "val v: (A, B) => C",
    after = "val v: (A, B) => C"
  )()

  def testExplicit(): Unit = check(
    before = "val v: Tuple2[A, B]",
    after = "val v: Tuple2[A, B]"
  )()
}
